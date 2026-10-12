package com.maquis.caisse.data.repository

import com.maquis.caisse.data.local.entity.DetteEntity
import com.maquis.caisse.domain.model.DetteStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the debt-consolidation edge case:
 *
 *  1. payOrder(DEBT) for half the order total  → dette created (OPEN)
 *  2. recordPaiement (partial repayment)        → dette updated  (PARTIAL)
 *  3. payOrder(DEBT) for the remaining half     → consolidation: originalAmount grows
 *
 * Asserts that after step 3 the dette has the correct
 * originalAmount, paidAmount, and status.
 *
 * Helper functions below mirror the exact logic in
 *   - OrderRepositoryImpl.payOrder (DEBT branch)
 *   - DetteRepositoryImpl.recordPaiement
 * so any change to those methods must be reflected here.
 */
class DebtConsolidationAfterPartialRepaymentTest {

    private val now = 1_700_000_000_000L

    // ── helpers that mirror repository logic ──────────────────────────────────

    /**
     * Mirrors [OrderRepositoryImpl.payOrder] — DEBT branch.
     *
     * When an existing dette is found it consolidates by summing originalAmount
     * and recomputing status so the displayed remaining balance stays correct.
     */
    private fun applyDebtPayment(existing: DetteEntity?, debtAmount: Long): DetteEntity {
        return if (existing != null) {
            val newOriginal = existing.originalAmount + debtAmount
            val recomputedStatus = when {
                existing.paidAmount >= newOriginal -> DetteStatus.SETTLED.storageKey
                existing.paidAmount > 0L -> DetteStatus.PARTIAL.storageKey
                else -> DetteStatus.OPEN.storageKey
            }
            existing.copy(originalAmount = newOriginal, status = recomputedStatus)
        } else {
            DetteEntity(
                id = 1L,
                customerName = "Alice",
                orderId = 42L,
                orderPublicId = "2026-08-07-001",
                originalAmount = debtAmount,
                paidAmount = 0L,
                status = DetteStatus.OPEN.storageKey,
                createdAt = now,
            )
        }
    }

    /**
     * Mirrors [DetteRepositoryImpl.recordPaiement].
     */
    private fun applyRepayment(dette: DetteEntity, amount: Long): DetteEntity {
        val newPaid = dette.paidAmount + amount
        val newStatus = when {
            newPaid >= dette.originalAmount -> DetteStatus.SETTLED.storageKey
            newPaid > 0L -> DetteStatus.PARTIAL.storageKey
            else -> DetteStatus.OPEN.storageKey
        }
        return dette.copy(paidAmount = newPaid, status = newStatus)
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    /**
     * Happy-path: partial repayment followed by second DEBT payment.
     * The dette must show the full original amount, the partial paidAmount,
     * and status = PARTIAL (remaining balance > 0).
     */
    @Test
    fun `partial repayment then second debt consolidation yields correct state`() {
        val total      = 10_000L
        val firstDebt  =  5_000L   // half the order
        val repayment  =  2_000L   // partial payment on the dette
        val secondDebt =  5_000L   // rest of the order paid via debt

        // Step 1 – first DEBT payment
        val after1stDebt = applyDebtPayment(null, firstDebt)
        assertEquals("originalAmount after 1st DEBT", firstDebt, after1stDebt.originalAmount)
        assertEquals("paidAmount after 1st DEBT", 0L, after1stDebt.paidAmount)
        assertEquals("status after 1st DEBT", DetteStatus.OPEN.storageKey, after1stDebt.status)

        // Step 2 – partial repayment
        val afterRepayment = applyRepayment(after1stDebt, repayment)
        assertEquals("originalAmount after repayment", firstDebt, afterRepayment.originalAmount)
        assertEquals("paidAmount after repayment", repayment, afterRepayment.paidAmount)
        assertEquals("status after repayment", DetteStatus.PARTIAL.storageKey, afterRepayment.status)

        // Step 3 – second DEBT payment (consolidation)
        val finalDette = applyDebtPayment(afterRepayment, secondDebt)

        assertEquals("originalAmount must equal full order total", total, finalDette.originalAmount)
        assertEquals("paidAmount must be preserved from repayment", repayment, finalDette.paidAmount)
        assertEquals(
            "remaining balance must equal total minus repayment",
            total - repayment,
            finalDette.originalAmount - finalDette.paidAmount,
        )
        // 2 000 paid of 10 000 → still PARTIAL, not OPEN and not SETTLED
        assertEquals("status must be PARTIAL", DetteStatus.PARTIAL.storageKey, finalDette.status)
    }

    /**
     * Edge case: full repayment followed by a new DEBT payment on the same order.
     * Without recomputing status on consolidation, the dette would be left SETTLED
     * even though new debt was added — causing the remaining balance to appear as 0.
     * With status recomputation the consolidation correctly returns PARTIAL.
     */
    @Test
    fun `full repayment then additional debt consolidation recomputes status to PARTIAL`() {
        val firstDebt   = 5_000L
        val fullRepay   = 5_000L   // settles the first debt entirely
        val secondDebt  = 3_000L

        val dette1 = applyDebtPayment(null, firstDebt)
        val dette2 = applyRepayment(dette1, fullRepay)
        assertEquals("status after full repayment", DetteStatus.SETTLED.storageKey, dette2.status)

        // New DEBT on the same order — consolidation must un-settle the dette
        val dette3 = applyDebtPayment(dette2, secondDebt)

        assertEquals("originalAmount after consolidation", firstDebt + secondDebt, dette3.originalAmount)
        assertEquals("paidAmount preserved", fullRepay, dette3.paidAmount)
        assertEquals(
            "remaining balance must equal secondDebt",
            secondDebt,
            dette3.originalAmount - dette3.paidAmount,
        )
        // Previously SETTLED, but new debt means it should be PARTIAL (5 000 paid of 8 000)
        assertEquals("status must be recomputed to PARTIAL", DetteStatus.PARTIAL.storageKey, dette3.status)
    }

    /**
     * Sanity: when no prior repayment exists the consolidation stays OPEN.
     */
    @Test
    fun `consolidation without any repayment keeps status OPEN`() {
        val firstDebt  = 4_000L
        val secondDebt = 6_000L

        val dette1 = applyDebtPayment(null, firstDebt)
        val dette2 = applyDebtPayment(dette1, secondDebt)

        assertEquals("originalAmount sums both debts", firstDebt + secondDebt, dette2.originalAmount)
        assertEquals("paidAmount stays zero", 0L, dette2.paidAmount)
        assertEquals("status must remain OPEN", DetteStatus.OPEN.storageKey, dette2.status)
    }
}
