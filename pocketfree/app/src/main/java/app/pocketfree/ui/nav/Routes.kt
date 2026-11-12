package app.pocketfree.ui.nav

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val BUDGETS = "budgets"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val RATES = "rates"
    const val RECURRING = "recurring"
    const val BACKUP = "backup"
    const val SECURITY = "security"

    /** Add/edit screen. Args: id (Long, -1 = new) and type (EXPENSE/INCOME). */
    const val TX_PATTERN = "tx?id={id}&type={type}"
    fun add(type: String) = "tx?type=$type"
    fun edit(id: Long) = "tx?id=$id"
}
