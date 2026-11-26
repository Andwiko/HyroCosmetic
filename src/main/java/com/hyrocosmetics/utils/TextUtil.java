package com.hyrocosmetics.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public class TextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    public static Component parse(String message) {
        if (message == null || message.isEmpty()) return Component.empty();

        Component comp;
        if (message.contains("<") && message.contains(">")) {
            try {
                comp = MINI_MESSAGE.deserialize(message);
            } catch (Exception e) {
                comp = LEGACY_AMPERSAND.deserialize(message);
            }
        } else if (message.contains("&")) {
            comp = LEGACY_AMPERSAND.deserialize(message);
        } else if (message.contains("§")) {
            comp = LEGACY_SECTION.deserialize(message);
        } else {
            comp = Component.text(message);
        }

        if (comp.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
            comp = comp.decoration(TextDecoration.ITALIC, false);
        }
        return comp;
    }


    public static List<Component> parseList(List<String> lines) {
        List<Component> list = new ArrayList<>();
        if (lines == null) return list;
        for (String s : lines) {
            list.add(parse(s));
        }
        return list;
    }
}
