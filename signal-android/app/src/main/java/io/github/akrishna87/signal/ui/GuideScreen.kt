package io.github.akrishna87.signal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akrishna87.signal.Rating

/** Plain-language help: what the numbers mean and what to do about a weak signal. */
@Composable
fun GuideScreen() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("Reading the big number") {
                Para(
                    "Signal is measured in dBm, and it's always negative. Closer to zero is stronger: " +
                        "−75 is much better than −105. Every 10 steps is ten times more or less power.",
                )
                Para("For 4G and 5G (RSRP):")
                Scale(Rating.EXCELLENT, "−80 or better")
                Scale(Rating.GOOD, "−80 to −90")
                Scale(Rating.FAIR, "−90 to −100")
                Scale(Rating.POOR, "−100 to −110")
                Scale(Rating.VERY_POOR, "below −110")
                Para("3G (RSCP) and 2G (RSSI) use slightly different scales; the colours already take that into account.")
            }
        }
        item {
            SectionCard("Strength vs quality") {
                Para(
                    "Strength (RSRP) is how loud the tower is. Quality (SINR) is how clearly it can be " +
                        "heard over noise and other towers. You can have a strong signal and still get slow " +
                        "data if quality is low, which is common in crowded areas.",
                )
                Para("SINR: 20 dB or more is excellent, 13 to 20 good, 5 to 13 fair, 0 to 5 poor, below 0 very poor.")
                Para("RSRQ (−3 to −20 dB) is another quality measure: −10 or better is good, below −15 is poor.")
            }
        }
        item {
            SectionCard("4G, 5G and \"5G (on 4G)\"") {
                Para(
                    "5G comes in two kinds. Standalone 5G (shown as \"5G\") uses only 5G towers. " +
                        "5G on 4G (also called NSA) keeps a 4G connection for control and adds 5G for " +
                        "speed. In India, Jio mostly uses standalone 5G and Airtel uses 5G on 4G.",
                )
                Para("\"4G+\" means the phone is joining several 4G bands together for more speed.")
            }
        }
        item {
            SectionCard("Bands") {
                Para(
                    "Low bands (700, 800, 850, 900 MHz) reach further and get through walls better. " +
                        "High bands (1800, 2100, 2300, 2500 MHz and 5G's 3500 MHz) are faster but weaker indoors. " +
                        "So a weaker number on a high band can still be faster than a stronger one on a low band.",
                )
            }
        }
        item {
            SectionCard("If your signal is weak") {
                Para("• Move near a window, especially one facing the tower, or go up a floor.")
                Para("• Concrete, metal, tinted glass and basements block signal the most.")
                Para("• Use the Best spot tab to find where in your home it's strongest.")
                Para("• Turn on Wi-Fi calling in your phone's SIM settings, so calls go over Wi-Fi.")
                Para("• If you have two SIMs, compare them: the other operator may have a closer tower.")
                Para("• Readings jump around by a few dBm all the time; look at the 10-minute graph, not one number.")
            }
        }
        item {
            SectionCard("Privacy") {
                Para(
                    "Alaimaani has no internet access. Measurements stay on your phone. Location " +
                        "permission is only needed because Android hides tower details without it; the " +
                        "app never reads where you are.",
                )
            }
        }
    }
}

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Scale(rating: Rating, range: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RatingDot(rating, size = 12)
        Text(rating.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text("$range dBm", style = MaterialTheme.typography.bodyMedium)
    }
}
