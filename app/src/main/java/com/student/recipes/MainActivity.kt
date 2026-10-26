package com.student.recipes

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray

// One recipe from assets/recipes.json
data class Recipe(
    val name: String,
    val emoji: String,
    val time: Int,
    val ingredients: List<String>
)

class MainActivity : Activity() {

    private lateinit var recipes: List<Recipe>
    private lateinit var allIngredients: List<String>
    private val selected = linkedSetOf<String>()

    private lateinit var search: EditText
    private lateinit var chips: FlowLayout
    private lateinit var selectedBar: TextView
    private lateinit var onlyReady: CheckBox
    private lateinit var resultsTitle: TextView
    private lateinit var results: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        search = findViewById(R.id.search)
        chips = findViewById(R.id.chips)
        selectedBar = findViewById(R.id.selectedBar)
        onlyReady = findViewById(R.id.onlyReady)
        resultsTitle = findViewById(R.id.resultsTitle)
        results = findViewById(R.id.results)

        // "Fridge" in white, "Chef" in gold, like the website logo
        findViewById<TextView>(R.id.logoText).text =
            Html.fromHtml("Fridge<font color='#FFD59E'>Chef</font>", Html.FROM_HTML_MODE_LEGACY)

        for (id in listOf(R.id.howCard, R.id.pickCard, R.id.resultsCard)) {
            findViewById<View>(id).background = rounded(R.color.white, 18f)
        }
        for (id in listOf(R.id.step1, R.id.step2, R.id.step3)) {
            findViewById<View>(id).background = rounded(R.color.orange_light, 14f)
        }

        recipes = loadRecipes()
        allIngredients = recipes.flatMap { it.ingredients }.distinct().sorted()

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = renderChips()
        })
        onlyReady.setOnCheckedChangeListener { _, _ -> renderResults() }
        findViewById<Button>(R.id.clearButton).setOnClickListener { clearAll() }

        renderChips()
        renderResults()
    }

    // Read the recipe list from assets/recipes.json
    private fun loadRecipes(): List<Recipe> {
        val text = assets.open("recipes.json").bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val ing = o.getJSONArray("ingredients")
            Recipe(
                name = o.getString("name"),
                emoji = o.optString("emoji", "🍽️"),
                time = o.optInt("time", 0),
                ingredients = (0 until ing.length()).map { ing.getString(it) }
            )
        }
    }

    private fun renderChips() {
        val q = search.text.toString().trim().lowercase()
        chips.removeAllViews()
        for (ingredient in allIngredients.filter { it.contains(q) }) {
            val isSelected = ingredient in selected
            chips.addView(TextView(this).apply {
                text = (if (isSelected) "✓ " else "+ ") + ingredient
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(getColor(if (isSelected) R.color.white else R.color.green))
                background = rounded(if (isSelected) R.color.green else R.color.green_light, 999f)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener {
                    if (isSelected) selected.remove(ingredient) else selected.add(ingredient)
                    renderChips()
                    renderResults()
                }
            })
        }
    }

    private fun renderResults() {
        selectedBar.text = if (selected.isEmpty()) {
            "No ingredients selected yet."
        } else {
            Html.fromHtml(
                "You selected <b><font color='#F28C28'>${selected.size}</font></b> ingredient(s): " +
                    selected.joinToString(", "),
                Html.FROM_HTML_MODE_LEGACY
            )
        }

        results.removeAllViews()

        if (selected.isEmpty()) {
            resultsTitle.text = "Suggested recipes"
            results.addView(emptyMessage("🥕\nSelect some ingredients above to see recipe suggestions."))
            return
        }

        // Same scoring as the website: fewest missing first, then highest match
        var scored = recipes
            .map { r -> Triple(r, r.ingredients.filter { it in selected }, r.ingredients.filterNot { it in selected }) }
            .filter { it.second.isNotEmpty() }

        if (onlyReady.isChecked) scored = scored.filter { it.third.isEmpty() }

        scored = scored.sortedWith(
            compareBy<Triple<Recipe, List<String>, List<String>>> { it.third.size }
                .thenByDescending { it.second.size.toDouble() / it.first.ingredients.size }
        )

        resultsTitle.text = "Suggested recipes (${scored.size})"

        if (scored.isEmpty()) {
            results.addView(emptyMessage("🤔\nNo matching recipes. Try adding more ingredients."))
            return
        }

        for ((r, have, missing) in scored) results.addView(recipeCard(r, have, missing))
    }

    private fun recipeCard(r: Recipe, have: List<String>, missing: List<String>): View {
        val ready = missing.isEmpty()
        val accent = if (ready) R.color.green else R.color.orange
        val pct = Math.round(have.size * 100.0 / r.ingredients.size).toInt()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(R.color.white, 16f)
            elevation = dp(3).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(2), dp(6), dp(2), dp(10)) }
        }

        // Colored strip on top: green = ready to cook, orange = something missing
        card.addView(View(this).apply {
            background = GradientDrawable().apply {
                setColor(getColor(accent))
                cornerRadii = FloatArray(8) { if (it < 4) dp(16).toFloat() else 0f }
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(5))
        })

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        card.addView(body)

        if (ready) {
            body.addView(
                pill("✓ Ready to cook", R.color.green, R.color.white, bold = true),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            )
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(text(r.emoji, 30f, R.color.text))
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        titleBox.addView(text(r.name, 17f, R.color.text, bold = true))
        titleBox.addView(text("⏱ ${r.time} min", 13f, R.color.muted))
        top.addView(titleBox)
        body.addView(top)

        // Progress bar showing how many ingredients you have
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(R.color.bar_bg, 999f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
                .apply { topMargin = dp(10) }
        }
        bar.addView(View(this).apply {
            background = rounded(accent, 999f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pct.toFloat())
        })
        bar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (100 - pct).toFloat())
        })
        body.addView(bar)

        body.addView(text("$pct% match · ${missing.size} missing", 14f, R.color.text, bold = true).apply {
            setPadding(0, dp(8), 0, dp(6))
        })

        val ing = FlowLayout(this)
        for (i in have) ing.addView(pill(i, R.color.green_light, R.color.green))
        for (i in missing) ing.addView(pill(i, R.color.miss_bg, R.color.miss_text).apply {
            paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        })
        body.addView(ing)

        return card
    }

    private fun clearAll() {
        selected.clear()
        search.setText("")
        onlyReady.isChecked = false
        renderChips()
        renderResults()
    }

    private fun emptyMessage(message: String) = text(message, 15f, R.color.muted).apply {
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(30), dp(10), dp(30))
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(getColor(color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun pill(value: String, bg: Int, fg: Int, bold: Boolean = false) =
        text(value, 12f, fg, bold).apply {
            background = rounded(bg, 999f)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(getColor(color))
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
