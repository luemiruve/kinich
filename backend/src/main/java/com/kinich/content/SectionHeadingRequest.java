package com.kinich.content;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Los máximos de @Size son una primera barrera; las reglas por sección están en SectionHeadingService. */
public record SectionHeadingRequest(@Size(max = 500) String eyebrow, @Size(max = 1000) String title,
                                    @Size(max = 3000) String body, @NotNull Long revision) {}
