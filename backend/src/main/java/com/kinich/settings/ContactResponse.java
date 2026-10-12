package com.kinich.settings;

public record ContactResponse(String email, String whatsapp, String phone, String location, String address,
                              String openingHours) {

    static ContactResponse from(BusinessSettings settings) {
        return new ContactResponse(settings.getEmail(), settings.getWhatsapp(), settings.getPhone(),
                settings.getLocation(), settings.getAddress(), settings.getOpeningHours());
    }
}
