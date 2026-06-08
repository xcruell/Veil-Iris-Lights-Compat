package com.yesmenn.veilirislights.client.screen;

import com.yesmenn.veilirislights.config.LightRenderConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public final class LightRenderConfigScreen extends Screen {

    private static final int PANEL_WIDTH = 310;
    private static final int PANEL_HEIGHT = 194;
    private static final int SLIDER_TOP_OFFSET = 32;

    private final Screen parent;

    public LightRenderConfigScreen(Screen parent) {
        super(Component.translatable("screen.veil_iris_lights.config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int y = top + SLIDER_TOP_OFFSET;
        LightRenderConfig config = LightRenderConfig.get();

        this.addRenderableWidget(new ConfigSlider(
                left, y, PANEL_WIDTH, "option.veil_iris_lights.exposure",
                0.01, LightRenderConfig.MAX_EXPOSURE,
                () -> config.exposure, value -> config.exposure = value));
        this.addRenderableWidget(new ConfigSlider(
                left, y + 26, PANEL_WIDTH, "option.veil_iris_lights.color_strength",
                0.0, LightRenderConfig.MAX_COLOR_STRENGTH,
                () -> config.colorStrength, value -> config.colorStrength = value));
        this.addRenderableWidget(new ConfigSlider(
                left, y + 52, PANEL_WIDTH, "option.veil_iris_lights.color_saturation",
                0.5, 2.0, () -> config.colorSaturation, value -> config.colorSaturation = value));
        this.addRenderableWidget(new ConfigSlider(
                left, y + 78, PANEL_WIDTH, "option.veil_iris_lights.neutral_lift",
                0.0, 2.0, () -> config.neutralLift, value -> config.neutralLift = value));
        this.addRenderableWidget(new ConfigSlider(
                left, y + 104, PANEL_WIDTH, "option.veil_iris_lights.luminance_limit",
                1.0, LightRenderConfig.MAX_LUMINANCE_BOOST_LIMIT,
                () -> config.luminanceBoostLimit, value -> config.luminanceBoostLimit = value));

        this.addRenderableWidget(Button.builder(
                Component.translatable("controls.reset"),
                button -> {
                    config.reset();
                    this.rebuildWidgets();
                }).bounds(left, y + 142, 150, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> this.onClose()).bounds(left + 160, y + 142, 150, 20).build());
    }

    @Override
    public void onClose() {
        LightRenderConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = (this.height - PANEL_HEIGHT) / 2;
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top, 0xFFFFFF);
        graphics.drawCenteredString(
                this.font,
                Component.translatable("screen.veil_iris_lights.live_hint"),
                this.width / 2,
                top + 14,
                0xA0A0A0);
    }

    private static final class ConfigSlider extends AbstractSliderButton {

        private final String translationKey;
        private final double min;
        private final double max;
        private final DoubleConsumer setter;

        private ConfigSlider(int x, int y, int width, String translationKey,
                             double min, double max, DoubleSupplier getter, DoubleConsumer setter) {
            super(x, y, width, 20, Component.empty(), normalize(getter.getAsDouble(), min, max));
            this.translationKey = translationKey;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable(this.translationKey, String.format("%.2f", this.currentValue())));
        }

        @Override
        protected void applyValue() {
            this.setter.accept(this.currentValue());
        }

        private double currentValue() {
            return this.min + this.value * (this.max - this.min);
        }

        private static double normalize(double value, double min, double max) {
            return (value - min) / (max - min);
        }
    }
}
