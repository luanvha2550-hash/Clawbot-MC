package net.luanvha2550_hash.FunctionCaller;

import java.util.Map;

@FunctionalInterface
public interface ToolStateUpdater {
    void update(Map<String, Object> sharedState, Map<String, String> paramMap, Object functionResult);
}
