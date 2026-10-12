package com.kinich.settings;

/** Contacto ya normalizado (teléfonos en E.164) y validado. Todos los campos son opcionales. */
record ContactValues(String email, String whatsapp, String phone, String location, String address,
                     String openingHours) {}
