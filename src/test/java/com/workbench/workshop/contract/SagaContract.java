package com.workbench.workshop.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lê o contrato das mensagens da Saga ({@code contracts/saga-messages.json}, cópia da
 * fonte única em workbench-messaging/contracts) e confere payloads contra ele.
 * Mesmo arquivo nos três serviços da Saga.
 */
public final class SagaContract {

    private final List<Map<String, Object>> messages;

    private SagaContract(List<Map<String, Object>> messages) {
        this.messages = messages;
    }

    @SuppressWarnings("unchecked")
    public static SagaContract load() {
        try (InputStream in = SagaContract.class.getResourceAsStream("/contracts/saga-messages.json")) {
            if (in == null) {
                throw new IllegalStateException("contracts/saga-messages.json não está no classpath de teste");
            }
            Map<String, Object> root = new ObjectMapper().readValue(in, new TypeReference<>() {});
            return new SagaContract((List<Map<String, Object>>) root.get("messages"));
        } catch (IOException e) {
            throw new IllegalStateException("Contrato da Saga ilegível", e);
        }
    }

    /** Tipos em que o serviço é emissor ou consumidor e precisa implementar o seu lado. */
    public Set<String> typesImplementedBy(String service) {
        Set<String> types = new TreeSet<>();
        for (Map<String, Object> m : messages) {
            boolean participates = service.equals(m.get("producer")) || service.equals(m.get("consumer"));
            if (participates && !optionalFor(m).contains(service)) {
                types.add((String) m.get("type"));
            }
        }
        return types;
    }

    /** Todos os tipos em que o serviço aparece (inclusive os opcionais para ele). */
    public Set<String> typesInvolving(String service) {
        Set<String> types = new TreeSet<>();
        for (Map<String, Object> m : messages) {
            if (service.equals(m.get("producer")) || service.equals(m.get("consumer"))) {
                types.add((String) m.get("type"));
            }
        }
        return types;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> example(String type) {
        return (Map<String, Object>) message(type).get("example");
    }

    /** Lista os campos obrigatórios do contrato que faltam no payload (vazia = conforme). */
    @SuppressWarnings("unchecked")
    public List<String> missingFields(String type, Map<String, Object> payload) {
        List<String> missing = new ArrayList<>();
        for (String path : (List<String>) message(type).get("required")) {
            if (!present(payload, path)) {
                missing.add(path);
            }
        }
        return missing;
    }

    /** Valores das constantes String públicas de uma classe (ex.: SagaMessages). */
    public static Set<String> constantValues(Class<?> holder) {
        Set<String> values = new TreeSet<>();
        for (Field f : holder.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && f.getType() == String.class) {
                try {
                    values.add((String) f.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static boolean present(Map<String, Object> payload, String path) {
        if (!path.contains("[].")) {
            return payload.containsKey(path);
        }
        String list = path.substring(0, path.indexOf("[]."));
        String field = path.substring(path.indexOf("[].") + 3);
        Object value = payload.get(list);
        if (!(value instanceof List<?> items)) {
            return false;
        }
        return items.stream().allMatch(i -> i instanceof Map<?, ?> m && ((Map<String, Object>) m).containsKey(field));
    }

    @SuppressWarnings("unchecked")
    private static List<String> optionalFor(Map<String, Object> m) {
        Object o = m.get("optionalFor");
        return o == null ? List.of() : (List<String>) o;
    }

    private Map<String, Object> message(String type) {
        return messages.stream().filter(m -> type.equals(m.get("type"))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo fora do contrato: " + type));
    }
}
