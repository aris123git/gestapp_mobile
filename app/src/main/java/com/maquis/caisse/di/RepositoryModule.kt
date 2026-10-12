package com.maquis.caisse.di

import com.maquis.caisse.data.repository.AvoirRepositoryImpl
import com.maquis.caisse.data.repository.CaisseSessionRepositoryImpl
import com.maquis.caisse.data.repository.CategoryRepositoryImpl
import com.maquis.caisse.data.repository.DetteRepositoryImpl
import com.maquis.caisse.data.repository.ExpenseRepositoryImpl
import com.maquis.caisse.data.repository.OrderRepositoryImpl
import com.maquis.caisse.data.repository.ProductRepositoryImpl
import com.maquis.caisse.data.repository.SaleRepositoryImpl
import com.maquis.caisse.data.repository.SettingsRepositoryImpl
import com.maquis.caisse.data.repository.StockRepositoryImpl
import com.maquis.caisse.data.repository.UserRepositoryImpl
import com.maquis.caisse.domain.repository.AvoirRepository
import com.maquis.caisse.domain.repository.CaisseSessionRepository
import com.maquis.caisse.domain.repository.CategoryRepository
import com.maquis.caisse.domain.repository.DetteRepository
import com.maquis.caisse.domain.repository.ExpenseRepository
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.domain.repository.ProductRepository
import com.maquis.caisse.domain.repository.SaleRepository
import com.maquis.caisse.domain.repository.SettingsRepository
import com.maquis.caisse.domain.repository.StockRepository
import com.maquis.caisse.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository

    @Binds @Singleton
    abstract fun bindSaleRepository(impl: SaleRepositoryImpl): SaleRepository

    @Binds @Singleton
    abstract fun bindOrderRepository(impl: OrderRepositoryImpl): OrderRepository

    @Binds @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds @Singleton
    abstract fun bindStockRepository(impl: StockRepositoryImpl): StockRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindCaisseSessionRepository(impl: CaisseSessionRepositoryImpl): CaisseSessionRepository

    @Binds @Singleton
    abstract fun bindDetteRepository(impl: DetteRepositoryImpl): DetteRepository

    @Binds @Singleton
    abstract fun bindAvoirRepository(impl: AvoirRepositoryImpl): AvoirRepository

    @Binds @Singleton
    abstract fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository
}
