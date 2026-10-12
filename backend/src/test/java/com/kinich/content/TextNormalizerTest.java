package com.kinich.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kinich.shared.error.ApiProblemException;
import com.kinich.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void trimsAndTurnsBlankOptionalsIntoNull() {
        TextValues values = TextNormalizer.normalize("  Eyebrow ", " \n\t ", "  Texto  ");

        assertThat(values).isEqualTo(new TextValues("Eyebrow", null, "Texto"));
    }

    @Test
    void normalizesLineBreaks() {
        assertThat(TextNormalizer.normalize(null, null, "a\r\nb\rc").body()).isEqualTo("a\nb\nc");
    }

    @Test
    void keepsTabsAndNewLinesInsideTheText() {
        assertThat(TextNormalizer.normalize(null, null, "uno\n\tdos").body()).isEqualTo("uno\n\tdos");
    }

    @Test
    void rejectsControlCharactersWithoutEchoingTheValue() {
        assertThatThrownBy(() -> TextNormalizer.normalize("ok\u0000", null, "Texto\u0007"))
                .isInstanceOfSatisfying(ApiProblemException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.getFieldErrors()).containsOnlyKeys("eyebrow", "body");
                    assertThat(ex.getFieldErrors().values()).noneMatch(message -> message.contains("ok"));
                });
    }

    @Test
    void bodyIsRequired() {
        assertThatThrownBy(() -> TextNormalizer.normalize(null, null, " \n "))
                .isInstanceOfSatisfying(ApiProblemException.class,
                        ex -> assertThat(ex.getFieldErrors()).containsOnlyKeys("body"));
    }

    @Test
    void acceptsEmojis() {
        String emojis = "📷".repeat(1000);

        assertThat(TextNormalizer.normalize(null, null, emojis).body()).isEqualTo(emojis);
    }
}
