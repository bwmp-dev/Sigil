package dev.bwmp.sigil.api.ability;

/**
 * A factory that builds an {@link Ability} from YAML parameters.
 * <p>
 * This is what lets an item be defined entirely in config. A server admin
 * writing {@code abilities: [{type: sigil:projectile, speed: 1.6}]} needs no
 * Java, and a plugin registering a new type makes it available to every YAML
 * item on the server.
 */
public interface AbilityType {

    /**
     * @param id     the ability id from config, unique within its item
     * @param name   display name from config
     * @param config the remaining keys of the ability's YAML block
     * @throws IllegalArgumentException when required parameters are missing or
     *                                  invalid; the message is shown to the
     *                                  admin against the offending file
     */
    Ability create(String id, String name, AbilityConfig config);

    /** One line describing the parameters this type accepts, for documentation. */
    default String describeParameters() {
        return "";
    }
}
