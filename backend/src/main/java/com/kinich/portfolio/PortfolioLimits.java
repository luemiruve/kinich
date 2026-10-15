package com.kinich.portfolio;

import java.util.regex.Pattern;

/** Límites técnicos del Portafolio (proyectos, galería y textos). */
public final class PortfolioLimits {

    public static final int MAX_PROJECTS = 200;
    public static final int MAX_IMAGES = 60;
    public static final int SLUG_MAX = 80;
    public static final int TITLE_MAX = 200;
    public static final int EYEBROW_MAX = 120;
    public static final int DESCRIPTION_MAX = 2000;
    public static final int ALT_MAX = 300;
    public static final int CAPTION_MAX = 300;
    public static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private PortfolioLimits() {
    }
}
