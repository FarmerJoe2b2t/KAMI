package me.zeroeightsix.kami.event.events;

import me.zeroeightsix.kami.event.KamiEvent;
import net.minecraft.client.gui.GuiScreen;

/**
 * Created by 086 on 17/11/2017.
 */
public class GuiScreenEvent extends KamiEvent {

    private GuiScreen screen;

    public GuiScreenEvent(GuiScreen screen) {
        super();
        this.screen = screen;
    }

    public GuiScreen getScreen() {
        return screen;
    }

    public void setScreen(GuiScreen screen) {
        this.screen = screen;
    }

    public static class Displayed extends GuiScreenEvent {
        public Displayed(GuiScreen screen) {
            super(screen);
        }
    }

    public static class Closed extends GuiScreenEvent {
        public Closed(GuiScreen screen) {
            super(screen);
        }
    }

}
