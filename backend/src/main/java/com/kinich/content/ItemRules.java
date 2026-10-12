package com.kinich.content;

import java.util.Set;

/** maxItems null = sin máximo. framing: cómo se encuadra la imagen en esta sección. */
public record ItemRules(Integer maxItems, Set<ItemField> allowed, Set<ItemField> required, ItemFraming framing) {}
