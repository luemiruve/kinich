package com.kinich.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ItemSectionPolicyTest {

    @Test
    void itemSectionsAndTheirRules() {
        assertThat(ItemSectionPolicy.rules(SectionKey.HERO).maxItems()).isEqualTo(1);
        assertThat(ItemSectionPolicy.rules(SectionKey.HERO).allowed()).containsExactly(ItemField.EYEBROW);
        assertThat(ItemSectionPolicy.rules(SectionKey.DISCIPLINES).maxItems()).isEqualTo(4);
        assertThat(ItemSectionPolicy.rules(SectionKey.DISCIPLINES).required()).containsExactly(ItemField.TITLE);
        assertThat(ItemSectionPolicy.rules(SectionKey.FEATURED_SELECTION).maxItems()).isNull();
        assertThat(ItemSectionPolicy.rules(SectionKey.CLOSING_CTA).allowed()).isEmpty();
        assertThat(ItemSectionPolicy.isItemSection(SectionKey.VALUES)).isFalse();
        assertThat(ItemSectionPolicy.isItemSection(SectionKey.EDITORIAL_NOTE)).isFalse();
    }
}
