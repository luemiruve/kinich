package com.kinich.portfolio;

import com.kinich.content.SectionHeading;
import java.util.List;

/** Página pública del portafolio. Las cabeceras son null mientras no haya una publicada. */
public record PublicPortfolioPageResponse(SectionHeading pageHeader, SectionHeading closingCta,
                                          List<String> categories, List<PublicProjectCard> projects) {}
