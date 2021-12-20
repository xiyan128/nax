package life.xiyan.nax;

import java.util.HashMap;
import java.util.Map;

public class Environment {

    // reference to the enclosing variable
    final Environment enclosing;

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    // uses bare strings over tokens because when it comes to looking up variables, all identifiers
    // tokens with the same name should refer to the same variable
    private final Map<String, Object> values = new HashMap<>();

    void define(String name, Object value) {
        // when we add the key to the map, we don't check if it's already present
        values.put(name, value);
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }

        // walk the chain to find the find the variable in outer scope
        if (enclosing != null) return enclosing.get(name);

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }
}
