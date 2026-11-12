package app.pocketfree.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIcons {
    /** Keys are what is stored in the database. Keep existing keys stable. */
    val all: Map<String, ImageVector> = linkedMapOf(
        "restaurant" to Icons.Rounded.Restaurant,
        "shopping_basket" to Icons.Rounded.ShoppingBasket,
        "directions_bus" to Icons.Rounded.DirectionsBus,
        "local_gas_station" to Icons.Rounded.LocalGasStation,
        "shopping_bag" to Icons.Rounded.ShoppingBag,
        "movie" to Icons.Rounded.Movie,
        "home" to Icons.Rounded.Home,
        "bolt" to Icons.Rounded.Bolt,
        "wifi" to Icons.Rounded.Wifi,
        "medical_services" to Icons.Rounded.MedicalServices,
        "school" to Icons.Rounded.School,
        "flight" to Icons.Rounded.Flight,
        "spa" to Icons.Rounded.Spa,
        "subscriptions" to Icons.Rounded.Subscriptions,
        "family_restroom" to Icons.Rounded.FamilyRestroom,
        "payments" to Icons.Rounded.Payments,
        "work" to Icons.Rounded.Work,
        "storefront" to Icons.Rounded.Storefront,
        "trending_up" to Icons.Rounded.TrendingUp,
        "savings" to Icons.Rounded.Savings,
        "card_giftcard" to Icons.Rounded.CardGiftcard,
        "pets" to Icons.Rounded.Pets,
        "fitness_center" to Icons.Rounded.FitnessCenter,
        "sports_esports" to Icons.Rounded.SportsEsports,
        "local_cafe" to Icons.Rounded.LocalCafe,
        "checkroom" to Icons.Rounded.Checkroom,
        "build" to Icons.Rounded.Build,
        "phone" to Icons.Rounded.Phone,
        "cake" to Icons.Rounded.Cake,
        "fastfood" to Icons.Rounded.Fastfood,
        "category" to Icons.Rounded.Category
    )

    fun get(key: String?): ImageVector = all[key] ?: Icons.Rounded.Category
}

object CategoryColors {
    val palette: List<Int> = listOf(
        0xFFEF4444, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308, 0xFF22C55E, 0xFF14B8A6,
        0xFF06B6D4, 0xFF3B82F6, 0xFF6366F1, 0xFF8B5CF6, 0xFFD946EF, 0xFFEC4899,
        0xFF64748B, 0xFF94A3B8
    ).map { it.toInt() }

    fun color(argb: Int): Color = Color(argb)
}
