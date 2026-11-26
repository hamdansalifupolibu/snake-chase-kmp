package com.snakechase.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snakechase.app.domain.model.ALL_LEVELS_GOAL
import com.snakechase.app.domain.model.OrbSkin
import com.snakechase.app.domain.model.SkinRarity
import com.snakechase.app.domain.model.SnakeSkin
import com.snakechase.app.profile.Economy
import com.snakechase.app.ui.canvas.OrbPainter
import com.snakechase.app.ui.canvas.SkinPainter
import com.snakechase.app.ui.theme.ArcadeButton
import com.snakechase.app.ui.theme.ArcadeChoiceRow
import com.snakechase.app.ui.theme.ArcadeDialog
import com.snakechase.app.ui.theme.ArcadeIcon
import com.snakechase.app.ui.theme.ArcadeIconView
import com.snakechase.app.ui.theme.ArcadeInset
import com.snakechase.app.ui.theme.ArcadeScrim
import com.snakechase.app.ui.theme.ArcadeTag
import com.snakechase.app.ui.theme.Banana
import com.snakechase.app.ui.theme.Cream
import com.snakechase.app.ui.theme.Ink
import com.snakechase.app.ui.theme.Lime
import com.snakechase.app.ui.theme.Moss
import com.snakechase.app.ui.theme.OutlinedText
import com.snakechase.app.ui.theme.RatingStar
import com.snakechase.app.ui.theme.Sky
import com.snakechase.app.ui.theme.displayStyle
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Shop colour for each rarity tier. */
fun rarityColor(rarity: SkinRarity): Color = when (rarity) {
    SkinRarity.COMMON -> Cream
    SkinRarity.RARE -> Sky
    SkinRarity.EPIC -> Color(0xFFB98CFF)
    SkinRarity.LEGENDARY -> Banana
}

/** A live snake in [skin], slithering on the spot: the shop, victory card and unlock all use it. */
@Composable
fun SkinPreview(skin: SnakeSkin, modifier: Modifier = Modifier, segments: Int = 11, dimmed: Boolean = false) {
    val t by rememberInfiniteTransition(label = "preview").animateFloat(
        0f,
        600f,
        infiniteRepeatable(tween(600_000, easing = LinearEasing)),
        label = "t",
    )
    Canvas(modifier.graphicsLayer { alpha = if (dimmed) 0.35f else 1f }) {
        val w = size.width
        val h = size.height
        val r = h * 0.15f
        val spacing = r * 1.05f
        val headX = w * 0.82f
        val points = List(segments + 1) { i ->
            val x = headX - i * spacing
            Offset(x, h * 0.5f + h * 0.18f * sin(t * 3f - i * 0.55f))
        }.filter { it.x > -r }
        val radii = List(points.size) { i -> if (i == 0) r * 1.25f else r * (1f - i * 0.035f).coerceAtLeast(0.55f) }
        val angle = atan2(points[0].y - points[1].y, points[0].x - points[1].x)
        with(SkinPainter) { drawSkinnedSnake(points, radii, angle, skin, t) }
    }
}

private enum class ShopTab(val label: String) { SNAKES("Snakes"), ORBS("Orbs") }

@Composable
fun SkinSelectOverlay(
    unlockedSkins: Set<String>,
    activeSkin: SnakeSkin,
    onEquip: (SnakeSkin) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    coins: Int = 0,
    onBuy: (SnakeSkin) -> Unit = {},
    /** Best stars per level, to show progress toward the star skins. */
    levelStars: Map<Int, Int> = emptyMap(),
    unlockedOrbs: Set<String> = setOf(OrbSkin.SPARK.name),
    activeOrb: OrbSkin = OrbSkin.SPARK,
    onEquipOrb: (OrbSkin) -> Unit = {},
    onBuyOrb: (OrbSkin) -> Unit = {},
    /** Judge edition: every skin and orb can be worn, owned or not (config/Edition.kt). */
    everythingOpen: Boolean = false,
) {
    var tab by remember { mutableStateOf(ShopTab.SNAKES) }

    // Celebrate any skin that becomes ours while the shop is open (bought here).
    var knownSkins by remember { mutableStateOf(unlockedSkins) }
    var knownOrbs by remember { mutableStateOf(unlockedOrbs) }
    var celebrateSkin by remember { mutableStateOf<SnakeSkin?>(null) }
    var celebrateOrb by remember { mutableStateOf<OrbSkin?>(null) }
    LaunchedEffect(unlockedSkins) {
        SnakeSkin.entries.firstOrNull { it.name in unlockedSkins && it.name !in knownSkins }?.let { celebrateSkin = it }
        knownSkins = unlockedSkins
    }
    LaunchedEffect(unlockedOrbs) {
        OrbSkin.entries.firstOrNull { it.name in unlockedOrbs && it.name !in knownOrbs }?.let { celebrateOrb = it }
        knownOrbs = unlockedOrbs
    }

    Box(modifier.fillMaxSize()) {
        ArcadeDialog(title = "Skins", icon = ArcadeIcon.Palette, onClose = onClose, width = 860.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArcadeChoiceRow(ShopTab.entries.toList(), tab, { it.label }, { tab = it }, Modifier.width(300.dp))
                    Spacer(Modifier.weight(1f))
                    if (everythingOpen) {
                        ArcadeTag("Judge edition: every skin unlocked", Lime)
                        Spacer(Modifier.size(12.dp))
                    }
                    ArcadeIconView(ArcadeIcon.Coin, size = 18.dp)
                    Text("  $coins", style = displayStyle(16.sp, Banana))
                }
                when (tab) {
                    ShopTab.SNAKES -> SnakeShop(unlockedSkins, activeSkin, coins, levelStars, everythingOpen, onEquip, onBuy)
                    ShopTab.ORBS -> OrbShop(unlockedOrbs, activeOrb, coins, everythingOpen, onEquipOrb, onBuyOrb)
                }
            }
        }
        celebrateSkin?.let { skin -> SkinUnlockCelebration(skin) { celebrateSkin = null } }
        celebrateOrb?.let { orb -> OrbUnlockCelebration(orb) { celebrateOrb = null } }
    }
}

@Composable
private fun SnakeShop(
    unlockedSkins: Set<String>,
    activeSkin: SnakeSkin,
    coins: Int,
    levelStars: Map<Int, Int>,
    everythingOpen: Boolean,
    onEquip: (SnakeSkin) -> Unit,
    onBuy: (SnakeSkin) -> Unit,
) {
    var selected by remember { mutableStateOf(activeSkin) }
    fun owned(s: SnakeSkin) = s == SnakeSkin.DEFAULT || s.name in unlockedSkins
    fun usable(s: SnakeSkin) = everythingOpen || owned(s)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Showcase(selected.rarity, Modifier.fillMaxWidth().height(170.dp)) {
                SkinPreview(selected, Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 18.dp), segments = 14)
            }
            NamePlate(selected.displayName, selected.rarity)
            Text(selected.description, color = Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            com.snakechase.app.domain.model.SkinPerks.of(selected).let { p ->
                SkinRatings(listOf("Speed" to p.speed, "Agility" to p.agility, "Reach" to p.reach), p.trait + " · when you're the snake")
            }
            ArcadeInset(Modifier.fillMaxWidth()) {
                ArcadeIconView(if (usable(selected)) ArcadeIcon.Check else ArcadeIcon.Lock, tint = if (usable(selected)) Lime else Moss, size = 18.dp)
                Text(lockLine(owned(selected), usable(selected), selected.unlockHint), color = Cream, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                StarGoalProgress(selected, levelStars)
            }
            ShopAction(Economy.skinPrice(selected), usable(selected), selected == activeSkin, coins, { onEquip(selected) }, { onBuy(selected) })
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!everythingOpen) OwnedCount(SnakeSkin.entries.count { owned(it) }, SnakeSkin.entries.size)
            SnakeSkin.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { skin ->
                        ShopTile(skin.displayName, skin.rarity, skin == selected, usable(skin), skin == activeSkin, Modifier.weight(1f), { selected = skin }) {
                            SkinPreview(skin, Modifier.fillMaxSize(), segments = 8, dimmed = !usable(skin))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun OrbShop(
    unlockedOrbs: Set<String>,
    activeOrb: OrbSkin,
    coins: Int,
    everythingOpen: Boolean,
    onEquip: (OrbSkin) -> Unit,
    onBuy: (OrbSkin) -> Unit,
) {
    var selected by remember { mutableStateOf(activeOrb) }
    fun owned(o: OrbSkin) = o == OrbSkin.SPARK || o.name in unlockedOrbs
    fun usable(o: OrbSkin) = everythingOpen || owned(o)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Showcase(selected.rarity, Modifier.fillMaxWidth().height(170.dp)) {
                OrbPreview(selected, Modifier.fillMaxSize())
            }
            NamePlate(selected.displayName, selected.rarity)
            Text(selected.description, color = Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            com.snakechase.app.domain.model.SkinPerks.of(selected).let { p ->
                SkinRatings(listOf("Speed" to p.speed, "Dash" to p.dash, "Graze" to p.graze), p.trait)
            }
            ArcadeInset(Modifier.fillMaxWidth()) {
                val icon = when {
                    usable(selected) -> ArcadeIcon.Check
                    selected.goal != null -> ArcadeIcon.Target
                    else -> ArcadeIcon.Lock
                }
                ArcadeIconView(icon, tint = if (usable(selected)) Lime else Moss, size = 18.dp)
                Text(lockLine(owned(selected), usable(selected), selected.unlockHint), color = Cream, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            }
            ShopAction(selected.price, usable(selected), selected == activeOrb, coins, { onEquip(selected) }, { onBuy(selected) })
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!everythingOpen) OwnedCount(OrbSkin.entries.count { owned(it) }, OrbSkin.entries.size)
            OrbSkin.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { orb ->
                        ShopTile(orb.displayName, orb.rarity, orb == selected, usable(orb), orb == activeOrb, Modifier.weight(1f), { selected = orb }) {
                            OrbPreview(orb, Modifier.fillMaxSize(), dimmed = !usable(orb), small = true)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The status line: owned, open only because of the judge edition (with the normal rule), or locked. */
private fun lockLine(owned: Boolean, usable: Boolean, hint: String): String = when {
    owned -> "Unlocked"
    usable -> "Open in the judge edition. Normally: ${hint.replaceFirstChar { it.lowercase() }}"
    else -> hint
}

@Composable
private fun NamePlate(name: String, rarity: SkinRarity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedText(name, displayStyle(26.sp), fill = rarityColor(rarity), outlineWidth = 5.dp)
        Spacer(Modifier.size(8.dp))
        ArcadeTag(rarity.label, rarityColor(rarity))
    }
}

@Composable
private fun OwnedCount(owned: Int, total: Int) {
    Text("$owned / $total owned", color = Moss, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
}

/**
 * The orb in [orb], flying a figure-eight with its trail. The trail is worked out from the same
 * path a moment earlier, so the preview needs no game state at all.
 */
@Composable
fun OrbPreview(orb: OrbSkin, modifier: Modifier = Modifier, dimmed: Boolean = false, small: Boolean = false) {
    val t by rememberInfiniteTransition(label = "orb").animateFloat(
        0f,
        600f,
        infiniteRepeatable(tween(600_000, easing = LinearEasing)),
        label = "t",
    )
    Canvas(modifier.graphicsLayer { alpha = if (dimmed) 0.35f else 1f }) {
        val w = size.width
        val h = size.height
        val r = if (small) h * 0.27f else h * 0.17f
        val speed = if (small) 1.1f else 1.4f
        fun at(time: Float) = Offset(
            w / 2f + sin(time * speed) * (w / 2f - r * 1.8f),
            h / 2f + sin(time * speed * 2f) * (h / 2f - r * 1.8f) * 0.8f,
        )
        val trail = List(12) { k -> at(t - k * 0.045f) }
        with(OrbPainter) {
            drawOrbTrail(trail, r, orb, t)
            with(com.snakechase.app.ui.canvas.SkinAuras) { drawOrbAura(trail[0], r, orb, t) }
            drawOrbCore(trail[0], r, orb, t)
        }
    }
}

/** Big stage for the selected skin: a glow in its rarity colour, sunburst rays for epics and legendaries. */
@Composable
private fun Showcase(rarity: SkinRarity, modifier: Modifier, content: @Composable () -> Unit) {
    val spin by rememberInfiniteTransition(label = "rays").animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "spin")
    val color = rarityColor(rarity)
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.clip(shape).background(Color(0xFF0B1A14)).border(3.dp, Ink, shape)) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            if (rarity == SkinRarity.LEGENDARY || rarity == SkinRarity.EPIC) {
                rotate(spin, c) { drawRays(c, size.maxDimension, color.copy(alpha = if (rarity == SkinRarity.LEGENDARY) 0.16f else 0.09f), 12) }
            }
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), center = c, radius = size.minDimension * 0.9f), size.minDimension * 0.9f, c)
        }
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRays(c: Offset, len: Float, color: Color, count: Int) {
    val half = (PI / count / 2).toFloat()
    for (k in 0 until count) {
        val a = (2 * PI * k / count).toFloat()
        val ray = Path().apply {
            moveTo(c.x, c.y)
            lineTo(c.x + cos(a - half) * len, c.y + sin(a - half) * len)
            lineTo(c.x + cos(a + half) * len, c.y + sin(a + half) * len)
            close()
        }
        drawPath(ray, color)
    }
}

@Composable
private fun ShopTile(
    name: String,
    rarity: SkinRarity,
    selected: Boolean,
    owned: Boolean,
    equipped: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val tint = rarityColor(rarity)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) tint.copy(alpha = 0.22f) else Ink.copy(alpha = 0.35f))
            .border(if (selected) 3.dp else 2.dp, if (selected) tint else Ink, shape)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(40.dp)) {
            preview()
            when {
                equipped -> Box(
                    Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(Lime).border(2.dp, Ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { ArcadeIconView(ArcadeIcon.Check, size = 11.dp) }
                !owned -> ArcadeIconView(ArcadeIcon.Lock, tint = Cream, size = 14.dp, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(tint))
        Text(name, style = displayStyle(12.sp, if (owned) Cream else Moss), maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Three-star goal progress, e.g. ★★☆ on Level 1, for the star skins. */
@Composable
private fun StarGoalProgress(skin: SnakeSkin, levelStars: Map<Int, Int>) {
    val goal = skin.threeStarGoal
    if (goal == 0) return
    if (goal == ALL_LEVELS_GOAL) {
        val done = (1..5).count { (levelStars[it] ?: 0) >= 3 }
        Text("$done / 5", style = displayStyle(14.sp, Banana))
    } else {
        val have = levelStars[goal] ?: 0
        Row { repeat(3) { RatingStar(filled = it < have, size = 15.dp) } }
    }
}

/** Equip, buy, or "earn it" for whatever is selected; a null [price] means it can only be earned. */
@Composable
private fun ShopAction(price: Int?, owned: Boolean, equipped: Boolean, coins: Int, onEquip: () -> Unit, onBuy: () -> Unit) {
    when {
        equipped -> ArcadeTag("Equipped", Lime)
        owned -> ArcadeButton("Equip", icon = ArcadeIcon.Check, fill = Lime, onClick = onEquip)
        price != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArcadeButton("Buy $price", icon = ArcadeIcon.Coin, fill = Banana, enabled = coins >= price, onClick = onBuy)
            if (coins < price) Text("${price - coins} more coins needed", color = Moss, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        else -> Text("Can't be bought: earn it.", color = Moss, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * Full-screen "new skin" moment: sunburst in the rarity colour, the snake bursting in,
 * confetti falling. Tap anywhere (or the button) to carry on.
 */
@Composable
fun SkinUnlockCelebration(skin: SnakeSkin, onDone: () -> Unit) {
    UnlockCelebration("New skin!", skin.displayName, skin.rarity, skin.description, onDone) {
        SkinPreview(skin, Modifier.size(width = 320.dp, height = 110.dp), segments = 14)
    }
}

/** The same moment for an orb skin, earned in a run or bought in the shop. */
@Composable
fun OrbUnlockCelebration(orb: OrbSkin, onDone: () -> Unit) {
    val line = orb.goal?.let { "You did it: ${it.hint.replaceFirstChar { c -> c.lowercase() }}." } ?: orb.description
    UnlockCelebration("New orb!", orb.displayName, orb.rarity, line, onDone) {
        OrbPreview(orb, Modifier.size(width = 340.dp, height = 170.dp))
    }
}

@Composable
private fun UnlockCelebration(
    headline: String,
    name: String,
    rarity: SkinRarity,
    description: String,
    onDone: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val pop = remember { Animatable(0.3f) }
    val audio = org.koin.compose.koinInject<com.snakechase.app.platform.AudioManager>()
    LaunchedEffect(name) {
        // A soft, warm chime for every new skin or orb.
        audio.playSfx(com.snakechase.app.domain.model.SoundId.UNLOCK)
        pop.snapTo(0.3f)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f))
    }
    val loop = rememberInfiniteTransition(label = "celebrate")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(10_000, easing = LinearEasing)), label = "spin")
    val fall by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3_000, easing = LinearEasing)), label = "fall")
    val color = rarityColor(rarity)
    ArcadeScrim(onDismiss = onDone) {
        // On top of the scrim: whatever screen is underneath must not show through the text.
        Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.6f)))
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.42f)
            rotate(spin, c) { drawRays(c, size.maxDimension, color.copy(alpha = 0.18f), 16) }
            val palette = listOf(Banana, Lime, Sky, Color(0xFFFF4F7B), Cream, color)
            for (i in 0 until 40) {
                val seed = (i * 37) % 100 / 100f
                val y = ((seed + fall * (0.6f + (i % 5) * 0.12f)) % 1f) * size.height
                val x = ((i * 53) % 100 / 100f) * size.width + sin(fall * 6.28f + i) * 20f
                rotate(fall * 720f + i * 40f, Offset(x, y)) {
                    drawRect(palette[i % palette.size], Offset(x - 5f, y - 3f), androidx.compose.ui.geometry.Size(10f, 6f))
                }
            }
        }
        // Laid out at full size, then scaled down to fit short (phone landscape) screens,
        // so nothing gets squashed or pushed off the bottom.
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val fit = minOf(1f, maxHeight / 440.dp)
            Column(
                Modifier
                    .wrapContentSize(unbounded = true)
                    .graphicsLayer {
                        scaleX = pop.value * fit
                        scaleY = pop.value * fit
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedText(headline, displayStyle(46.sp), fill = color, outlineWidth = 8.dp, halo = Cream, haloWidth = 15.dp, shadow = 5.dp)
                preview()
                OutlinedText(name, displayStyle(28.sp), fill = Cream, outlineWidth = 5.dp)
                ArcadeTag(rarity.label, color)
                Text(description, color = Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                ArcadeButton("Awesome!", icon = ArcadeIcon.Check, fill = Banana, onClick = onDone)
            }
        }
    }
}

/** A skin's three ratings as 5-segment bars, plus its trait (see SkinPerks). */
@Composable
private fun SkinRatings(stats: List<Pair<String, Int>>, trait: String) {
    androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        for ((label, value) in stats) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(label, color = Cream.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                for (i in 1..5) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .padding(end = 3.dp)
                            .size(width = 18.dp, height = 8.dp)
                            .background(if (i <= value) Lime else Cream.copy(alpha = 0.18f), androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
        Text(trait, color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
