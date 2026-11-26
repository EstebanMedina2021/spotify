package com.playlist.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationFilterTest {

    private final List<CancionSnapshot> known = List.of(
            new CancionSnapshot("Bohemian Rhapsody", "Queen", "rock"),
            new CancionSnapshot("Levitating", "Dua Lipa", "pop"));

    private static RecomendacionItem item(String title, String artist) {
        return new RecomendacionItem(title, artist, "rock", "x");
    }

    @Test
    void songAlreadyInTheList_isDropped_ignoringCaseAccentsAndPunctuation() {
        List<RecomendacionItem> result = RecommendationFilter.novelItems(List.of(
                item("bohemian rhapsody!", "QUEEN"),
                item("Under Pressure", "Queen")), known);

        assertThat(result).extracting(RecomendacionItem::titulo).containsExactly("Under Pressure");
    }

    @Test
    void sameTitleByAnotherArtist_isADifferentSong_andKept() {
        List<RecomendacionItem> result = RecommendationFilter.novelItems(List.of(
                item("Bohemian Rhapsody", "Panic! At The Disco")), known);

        assertThat(result).hasSize(1);
    }

    @Test
    void artistWithFeaturedGuests_stillMatchesTheKnownSong() {
        List<RecomendacionItem> result = RecommendationFilter.novelItems(List.of(
                item("Levitating", "Dua Lipa, DaBaby")), known);

        assertThat(result).isEmpty();
    }

    @Test
    void artistGluedOntoTheTitle_isStripped() {
        List<RecomendacionItem> result = RecommendationFilter.novelItems(List.of(
                item("Don't Start Now - Dua Lipa", "Dua Lipa")), known);

        assertThat(result).extracting(RecomendacionItem::titulo).containsExactly("Don't Start Now");
    }

    @Test
    void gluedArtistThatMakesItAKnownSong_isStrippedBeforeComparing() {
        List<RecomendacionItem> result = RecommendationFilter.novelItems(List.of(
                item("Levitating - Dua Lipa", "Dua Lipa")), known);

        assertThat(result).isEmpty();
    }

    @Test
    void aDashInsideARealTitle_isNotTouched() {
        List<RecomendacionItem> result = RecommendationFilter.novelItems(List.of(
                item("Hey - Ho", "The Ramones")), known);

        assertThat(result).extracting(RecomendacionItem::titulo).containsExactly("Hey - Ho");
    }
}