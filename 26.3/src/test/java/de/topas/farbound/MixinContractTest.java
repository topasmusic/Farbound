package de.topas.farbound;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Static bytecode contract, deliberately no Fabric/Minecraft launch. */
class MixinContractTest {
    private ClassNode read(String name) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            assertNotNull(stream, name); var node = new ClassNode(); new ClassReader(stream).accept(node, ClassReader.SKIP_CODE); return node;
        }
    }
    @Test void allDeclaredInjectionAndInvokerTargetsExistInPinnedMinecraft() throws Exception {
        for (var pair : List.of(new String[]{"ServerPlayerMixin", "net.minecraft.server.level.ServerPlayer"},
                new String[]{"PlayerListMixin", "net.minecraft.server.players.PlayerList"}, new String[]{"PlayerListAccess", "net.minecraft.server.players.PlayerList"})) {
            var target = read(pair[1]); var mixin = read("de.topas.farbound.mixin." + pair[0]);
            for (var method : mixin.methods) {
                var annotations = new ArrayList<AnnotationNode>();
                if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
                if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
                for (var a : annotations) {
                    String key = a.desc.endsWith("/Inject;") ? "method" : a.desc.endsWith("/Invoker;") ? "value" : null;
                    if (key == null) continue;
                    for (int i = 0; i < a.values.size(); i += 2) if (key.equals(a.values.get(i))) {
                        Object raw = a.values.get(i + 1); var names = raw instanceof List<?> list ? list : List.of(raw);
                        for (Object n : names) {
                            String name = n.toString(); int paren = name.indexOf('(');
                            var matches = target.methods.stream().filter(m -> paren < 0 ? m.name.equals(name) : (m.name + m.desc).equals(name)).toList();
                            assertEquals(1, matches.size(), pair[0] + ": " + name);
                            if (key.equals("value")) assertEquals(method.desc, matches.getFirst().desc);
                            else {
                                var hookArgs = Type.getArgumentTypes(method.desc); var targetArgs = Type.getArgumentTypes(matches.getFirst().desc);
                                assertArrayEquals(targetArgs, Arrays.copyOf(hookArgs, hookArgs.length - 1), method.name);
                            }
                        }
                    }
                }
            }
        }
    }
}
