package com.mylittleblover.mkwplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.mylittleblover.mkwplayer.player.CatalogBrowser
import com.mylittleblover.mkwplayer.ui.Tokens

/**
 * The library landing page (UI-01 / UI-02, with the search and recently-played entry points of UI-14
 * and UI-19).
 *
 * The composable holds no rules. It receives a [LibraryHeader], the already-gated [categories] and the
 * page [state], and prints them. Every count, every sentence and the decision whether a row may be
 * shown at all lives in `LibraryStates.kt`, where it can be tested on the JVM -- the reason is that
 * three of this page's decisions are claims about the soundtrack (a missing catalogue, a count that
 * ran through the cup gate, a search that must not bypass it), and a claim that only a screenshot
 * checks is a claim nobody checks.
 *
 * What the layout does own is the part the contract cares about visually:
 *
 * - A non-ready page still scrolls, so the words explaining the state cannot fall off a short window.
 * - It draws no placeholder rows. A grey skeleton of five rows is the "empty page that looks like
 *   success" the contract forbids, and a fake row is worse than an honest sentence.
 * - Every clickable reaches [Tokens.Component.touchTargetMin], because a category row whose touch area
 *   is the height of its text is a footnote, not a control.
 *
 * The search and recent sections live in their own composables below because the parent screen does
 * not own them: UI-14 and UI-19 are separate pages in the contract, and a caller that only has room
 * for one of them should be able to place it without dragging the whole landing page along.
 */
@Composable
internal fun LibraryScreen(
    header: LibraryHeader,
    categories: List<CategoryRow>,
    state: PageState,
    onCategory: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Computed before the list so the same decision cannot be made twice and disagree with itself.
    val message = pageMessage(state, header)
    val showRows = state.canShowRows

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.Palette.cream100),
        contentPadding = PaddingValues(
            horizontal = Tokens.Spacing.md,
            vertical = Tokens.Spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        item(key = "header") {
            HeaderBlock(header = header, state = state)
        }

        item(key = "search") {
            OutlinedButton(
                onClick = onSearch,
                // The token, not the Material default: the default is 40 dp, below the 48 dp the pack
                // requires, and it would be wrong on the one control that is always on this page.
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.Component.touchTargetMin),
            ) {
                Text(
                    text = searchAction(),
                    fontSize = Tokens.Type.body,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // The page's own explanation. Present for every state that has one, including LOADING: a
        // bare spinner says nothing about whether anything was found.
        if (message != null) {
            item(key = "state-notice") {
                StateNotice(state = state, message = message)
            }
        }

        if (showRows) {
            if (categories.isEmpty()) {
                item(key = "no-categories") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Tokens.Radius.md),
                        colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
                    ) {
                        Text(
                            text = emptyCategoriesMessage(),
                            modifier = Modifier.padding(Tokens.Spacing.md),
                            fontSize = Tokens.Type.secondary,
                            color = Tokens.Palette.ink900,
                        )
                    }
                }
            } else {
                item(key = "categories-heading") {
                    Text(
                        text = categoriesHeading(),
                        modifier = Modifier.padding(top = Tokens.Spacing.xs),
                        fontSize = Tokens.Type.sectionTitle,
                        fontWeight = FontWeight.Bold,
                        color = Tokens.Palette.ink900,
                    )
                }
                items(categories, key = { it.name }) { row ->
                    CategoryCard(row = row, onClick = { onCategory(row.name) })
                }
            }
        }
    }
}

/**
 * The header: title, the state's own word, counts, and the two notices that must never be dropped.
 *
 * [LibraryHeader.coverageNotice] is shown whenever it is non-null and cannot be dismissed: on a
 * partial catalogue those numbers are a floor, and a screen that hid the notice would present the
 * floor as the whole soundtrack -- the one mistake the catalogue loader's own docs single out.
 *
 * Counts are printed only when [PageState.canCountTracks]. That is the guard against the mistake the
 * whole file is built around: printing "0 of 650" on a page that never read a catalogue states a
 * measured zero where no measurement happened.
 */
@Composable
private fun HeaderBlock(header: LibraryHeader, state: PageState) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = libraryTitle(),
                fontSize = Tokens.Type.pageTitle,
                fontWeight = FontWeight.Bold,
                color = Tokens.Palette.navy900,
            )
            // The contract's state name, shown as words rather than as a colour alone: the palette's
            // warning colour as text fails the contrast minimum the pack itself sets.
            Text(
                text = state.label,
                fontSize = Tokens.Type.caption,
                color = Tokens.Palette.navy700,
            )
        }

        Text(
            text = header.summary,
            fontSize = Tokens.Type.body,
            color = Tokens.Palette.ink900,
        )

        if (state.canCountTracks) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = presentCountLabel(header),
                    fontSize = Tokens.Type.secondary,
                    color = Tokens.Palette.ink900,
                )
                Text(
                    text = knownCountLabel(header),
                    fontSize = Tokens.Type.secondary,
                    color = Tokens.Palette.ink900,
                )
                Text(
                    text = playableCountLabel(header),
                    fontSize = Tokens.Type.secondary,
                    color = Tokens.Palette.ink900,
                )
            }
        }

        header.coverageNotice?.let { notice ->
            Text(
                text = notice,
                fontSize = Tokens.Type.secondary,
                color = Tokens.Palette.warningText,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The explanation for a non-ready page.
 *
 * Coloured by whether the state is a failure, but never coloured alone: the sentence is the message
 * and the colour is the emphasis, so a reader who cannot distinguish the two still gets the fact.
 */
@Composable
private fun StateNotice(state: PageState, message: String) {
    // The colour is emphasis, never the message: the sentence is the message, so a reader who cannot
    // tell red from amber still gets the fact.
    val accent = when (state) {
        PageState.ERROR,
        PageState.REBIND_REQUIRED,
        PageState.OFFLINE_OR_NO_PERMISSION,
        PageState.MISSING_ASSET -> Tokens.Palette.errorText

        PageState.UNVERIFIED,
        PageState.PENDING,
        PageState.LOCKED,
        PageState.RESTORED -> Tokens.Palette.warningText

        else -> Tokens.Palette.ink900
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.Radius.md),
        colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
    ) {
        Column(
            modifier = Modifier.padding(Tokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
        ) {
            Text(
                text = state.label,
                fontSize = Tokens.Type.secondary,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                text = message,
                fontSize = Tokens.Type.secondary,
                color = Tokens.Palette.ink900,
            )
        }
    }
}

/**
 * One category row.
 *
 * The whole row is the target rather than a trailing "open" button: a 48 dp row that only responds
 * on a 40 dp word is a hit-testing complaint waiting to happen, and the pack's touch-target rule is
 * about the area the finger lands on, not the label's height.
 *
 * A [CategoryRow.locked] row carries [hiddenRowsBadge], which says a subtraction happened without
 * naming what was removed. Silently showing the smaller total would be the other option, and it would
 * present a gated count as the category's real size.
 */
@Composable
private fun CategoryCard(row: CategoryRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.Radius.md),
        colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.Component.touchTargetMin)
                .clickable(onClick = onClick)
                .padding(Tokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.name,
                    fontSize = Tokens.Type.body,
                    fontWeight = FontWeight.Bold,
                    color = Tokens.Palette.ink900,
                )
                if (row.locked) {
                    Text(
                        text = hiddenRowsBadge(),
                        fontSize = Tokens.Type.caption,
                        color = Tokens.Palette.warningText,
                    )
                }
            }
            Text(
                text = categoryDetail(row),
                fontSize = Tokens.Type.secondary,
                color = Tokens.Palette.ink900,
            )
        }
    }
}

/**
 * The search page (UI-14).
 *
 * Query text and results are both hoisted: this composable stores nothing, so the search that runs is
 * always the search the caller's own `query` describes. A local `remember` here would let the box and
 * the results disagree for a frame, and the results are exactly the thing that must not disagree with
 * the gate.
 *
 * An empty result is never rendered as an empty column. [SearchResult.emptyReason] is the reason, and
 * when the gate produced it the reason does not name what was removed -- a search box that answers
 * "no result for Rainbow" has confirmed the course exists.
 *
 * [rowFor] turns the result's indices back into rows through the caller's current list, which is what
 * keeps a stale index from being drawn as if the track still existed.
 */
@Composable
internal fun LibrarySearch(
    query: String,
    onQueryChange: (String) -> Unit,
    result: SearchResult,
    rowFor: (Int) -> CatalogBrowser.Row?,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        Text(
            text = searchTitle(),
            modifier = Modifier.padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xs),
            fontSize = Tokens.Type.pageTitle,
            fontWeight = FontWeight.Bold,
            color = Tokens.Palette.navy900,
        )

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.md)
                .heightIn(min = Tokens.Component.touchTargetMin),
            singleLine = true,
            placeholder = {
                Text(text = searchHint(), fontSize = Tokens.Type.secondary)
            },
        )

        val reason = result.emptyReason
        if (reason != null) {
            // One sentence instead of an empty list, for the same reason the library page has one.
            Text(
                text = reason,
                modifier = Modifier.padding(horizontal = Tokens.Spacing.md),
                fontSize = Tokens.Type.secondary,
                color = Tokens.Palette.ink900,
            )
        }

        LazyColumn(
            // weight(1f) rather than fillMaxSize: inside a Column the weight is what reserves the
            // space left over by the title and the field, so the list cannot push them off-screen.
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = Tokens.Spacing.md,
                vertical = Tokens.Spacing.xs,
            ),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
        ) {
            items(result.rows) { index ->
                val row = rowFor(index)
                if (row != null) {
                    ResultRow(row = row, onClick = { onOpen(index) })
                }
            }
        }
    }
}

/**
 * One search result.
 *
 * The counts in the second line come from the row the caller passed, never from the query, so a result
 * cannot describe a track the caller no longer holds.
 */
@Composable
private fun ResultRow(row: CatalogBrowser.Row, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.Radius.sm),
        colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.Component.touchTargetMin)
                .clickable(onClick = onClick)
                .padding(Tokens.Spacing.sm),
        ) {
            Text(
                text = row.name,
                fontSize = Tokens.Type.body,
                color = Tokens.Palette.ink900,
            )
            Text(
                text = rowPartsLabel(row),
                fontSize = Tokens.Type.caption,
                color = Tokens.Palette.ink900,
            )
        }
    }
}

/**
 * The recently played list (UI-19).
 *
 * [emptyMessage] is supplied by the caller because only the caller knows whether the list is empty
 * because nothing was played or because the gate removed everything -- two different sentences, and a
 * screen that guessed would tell a user their history is empty when it is not.
 */
@Composable
internal fun RecentlyPlayed(
    entries: List<RecentEntry>,
    emptyMessage: String,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = Tokens.Spacing.md,
            vertical = Tokens.Spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
    ) {
        item(key = "recent-heading") {
            Text(
                text = recentHeading(),
                fontSize = Tokens.Type.sectionTitle,
                fontWeight = FontWeight.Bold,
                color = Tokens.Palette.navy900,
            )
        }

        if (entries.isEmpty()) {
            item(key = "recent-empty") {
                Text(
                    text = emptyMessage,
                    fontSize = Tokens.Type.secondary,
                    color = Tokens.Palette.ink900,
                )
            }
        } else {
            // No key: a recency list can legitimately hold one course twice, and a duplicate key makes
            // LazyColumn throw at runtime. Position is the only identifier guaranteed to be unique here.
            items(entries) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Tokens.Radius.sm),
                    colors = CardDefaults.cardColors(containerColor = Tokens.Palette.white),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Tokens.Component.touchTargetMin)
                            .clickable { onPlay(entry.key) }
                            .padding(Tokens.Spacing.sm),
                    ) {
                        Text(
                            text = entry.title,
                            fontSize = Tokens.Type.body,
                            color = Tokens.Palette.ink900,
                        )
                        Text(
                            text = entry.subtitle,
                            fontSize = Tokens.Type.caption,
                            color = Tokens.Palette.ink900,
                        )
                    }
                }
            }
        }
    }
}
