package com.maquis.caisse.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUserPermissionsTest {

    @Test
    fun caissierCanMarkPaidEvenWithEmptyStoredPermissions() {
        val caissier = AppUser(
            name = "Caissier",
            pin = "1234",
            role = "CAISSIER",
            permissions = emptySet(),
        )
        assertTrue(caissier.can(Permissions.MARK_PAID))
        assertTrue(caissier.can(Permissions.SELL))
        assertFalse(caissier.can(Permissions.CANCEL_ORDER))
        assertFalse(caissier.can(Permissions.MANAGE_USERS))
        assertFalse(caissier.can(Permissions.CREATE_PRODUCT))
        assertFalse(caissier.can(Permissions.EDIT_PRODUCT))
    }

    @Test
    fun serveuseCannotMarkPaid() {
        val serveuse = AppUser(
            name = "Aïcha",
            pin = "1234",
            role = "SERVEUSE",
            permissions = Permissions.SERVEUSE_DEFAULT.toSet(),
        )
        assertTrue(serveuse.can(Permissions.SELL))
        assertFalse(serveuse.can(Permissions.MARK_PAID))
        assertFalse(serveuse.can(Permissions.CANCEL_ORDER))
    }

    @Test
    fun adminCanEverything() {
        val admin = AppUser(
            name = "Admin",
            pin = "0000",
            role = "ADMIN",
            permissions = emptySet(),
        )
        assertTrue(admin.can(Permissions.MARK_PAID))
        assertTrue(admin.can(Permissions.CANCEL_ORDER))
        assertTrue(admin.can(Permissions.MANAGE_USERS))
    }
}
