/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.selection;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/**
 * Actionable validation result displayed by the selection overlay and settings GUI.
 */
public record SelectionValidation(
        Code code,
        Optional<SelectionBounds> bounds,
        String message,
        String suggestedAction) {

    public enum Code {
        VALID,
        INCOMPLETE,
        TOO_LARGE
    }

    public SelectionValidation {
        Objects.requireNonNull(code, "code");
        bounds = Objects.requireNonNull(bounds, "bounds");
        message = requireText(message, "message");
        suggestedAction = Objects.requireNonNull(suggestedAction, "suggestedAction");
        if (code == Code.INCOMPLETE && bounds.isPresent()) {
            throw new IllegalArgumentException("incomplete validation must not contain bounds");
        }
        if (code != Code.INCOMPLETE && bounds.isEmpty()) {
            throw new IllegalArgumentException("complete validation must contain bounds");
        }
    }

    public static SelectionValidation incomplete() {
        return new SelectionValidation(
                Code.INCOMPLETE,
                Optional.empty(),
                "始点と終点の両方を選択してください。",
                "未設定の角を選択してください。");
    }

    public static SelectionValidation validate(SelectionBounds bounds, long maximumBlockCount) {
        Objects.requireNonNull(bounds, "bounds");
        if (maximumBlockCount <= 0L) {
            throw new IllegalArgumentException("maximumBlockCount must be positive");
        }
        BigInteger maximum = BigInteger.valueOf(maximumBlockCount);
        if (bounds.blockCount().compareTo(maximum) > 0) {
            return new SelectionValidation(
                    Code.TOO_LARGE,
                    Optional.of(bounds),
                    "選択ブロック数 " + bounds.blockCount() + " は上限 " + maximumBlockCount + " を超えています。",
                    "選択範囲を縮小するか、Selection設定の上限を確認してください。");
        }
        return new SelectionValidation(
                Code.VALID,
                Optional.of(bounds),
                "選択範囲は有効です。",
                "");
    }

    public boolean valid() {
        return code == Code.VALID;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
