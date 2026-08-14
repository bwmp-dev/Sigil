package dev.bwmp.sigil.api.visual;

/** A spawned visual whose lifetime is controlled by the caller. */
public interface DisplayEffect {

    DisplayEffect NONE = new DisplayEffect() {
        @Override
        public boolean active() {
            return false;
        }

        @Override
        public void remove() {
        }
    };

    boolean active();

    void remove();
}
