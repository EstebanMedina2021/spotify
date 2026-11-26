package com.playlist.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playlist.recommendation.exception.AiInvalidResponseException;
import org.junit.jupiter.api.Test;

class AiResponseParserTest {

    private final AiResponseParser parser = new AiResponseParser(new ObjectMapper());

    @Test
    void validJson_isParsed() {
        String raw = """
                {"recomendaciones":[{"titulo":"Under Pressure","artista":"Queen","genero":"Rock","razon":"x"}]}""";

        RecommendationResult result = parser.parse(raw, "GEMINI", "v1");

        assertThat(result.recomendaciones()).hasSize(1);
        assertThat(result.fuente()).isEqualTo("GEMINI");
        assertThat(result.fallback()).isFalse();
    }

    @Test
    void codeFencedJson_isParsed() {
        String raw = "```json\n{\"recomendaciones\":[{\"titulo\":\"A\",\"artista\":\"B\"}]}\n```";

        assertThat(parser.parse(raw, "OLLAMA", "v1").recomendaciones()).hasSize(1);
    }

    @Test
    void moreThanFive_isTruncated() {
        StringBuilder sb = new StringBuilder("{\"recomendaciones\":[");
        for (int i = 0; i < 8; i++) {
            sb.append(i > 0 ? "," : "").append("{\"titulo\":\"T").append(i).append("\",\"artista\":\"A\"}");
        }
        sb.append("]}");

        assertThat(parser.parse(sb.toString(), "GEMINI", "v1").recomendaciones()).hasSize(5);
    }

    @Test
    void itemsWithoutTitleOrArtist_areDiscarded() {
        String raw = """
                {"recomendaciones":[{"titulo":"","artista":"X"},{"titulo":"T"},{"titulo":"Ok","artista":"Ok"}]}""";

        assertThat(parser.parse(raw, "GEMINI", "v1").recomendaciones())
                .extracting(RecomendacionItem::titulo).containsExactly("Ok");
    }

    @Test
    void unknownFields_areIgnored() {
        String raw = "{\"extra\":1,\"recomendaciones\":[{\"titulo\":\"T\",\"artista\":\"A\",\"foo\":\"bar\"}]}";

        assertThat(parser.parse(raw, "GEMINI", "v1").recomendaciones()).hasSize(1);
    }

    @Test
    void longReason_isTruncatedTo200() {
        String raw = "{\"recomendaciones\":[{\"titulo\":\"T\",\"artista\":\"A\",\"razon\":\""
                + "x".repeat(500) + "\"}]}";

        assertThat(parser.parse(raw, "GEMINI", "v1").recomendaciones().get(0).razon()).hasSize(200);
    }

    @Test
    void noValidItems_throwsInvalidResponse() {
        assertThatThrownBy(() -> parser.parse("{\"recomendaciones\":[{\"titulo\":\"T\"}]}", "GEMINI", "v1"))
                .isInstanceOf(AiInvalidResponseException.class);
    }

    @Test
    void malformedOrEmpty_throwsInvalidResponse() {
        assertThatThrownBy(() -> parser.parse("not json", "GEMINI", "v1"))
                .isInstanceOf(AiInvalidResponseException.class);
        assertThatThrownBy(() -> parser.parse("  ", "GEMINI", "v1"))
                .isInstanceOf(AiInvalidResponseException.class);
        assertThatThrownBy(() -> parser.parse(null, "GEMINI", "v1"))
                .isInstanceOf(AiInvalidResponseException.class);
        assertThatThrownBy(() -> parser.parse("{\"otro\":[]}", "GEMINI", "v1"))
                .isInstanceOf(AiInvalidResponseException.class);
    }

    @Test
    void errorMessage_doesNotLeakModelOutput() {
        assertThatThrownBy(() -> parser.parse("SECRET-LLM-OUTPUT", "GEMINI", "v1"))
                .hasMessageNotContaining("SECRET-LLM-OUTPUT");
    }
}
