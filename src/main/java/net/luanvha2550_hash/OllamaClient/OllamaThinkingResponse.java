package net.luanvha2550_hash.OllamaClient;

/**
 * Wrapper class for Ollama API responses that support thinking mode
 * Used by reasoning models like deepseek-r1
 */
public class OllamaThinkingResponse {
    private final String content;
    private final String thinking;
    private final boolean hasThinking;

    public OllamaThinkingResponse(String content, String thinking) {
        this.content = content;
        this.thinking = thinking;
        this.hasThinking = thinking != null && !thinking.isEmpty();
    }

    public String getContent() {
        return content;
    }

    public String getThinking() {
        return thinking;
    }

    public boolean hasThinking() {
        return hasThinking;
    }

    /**
     * Gets the full response combining thinking and content
     * Formats it in <pensamento> tags for compatibility with existing code
     */
    public String getFullResponse() {
        if (hasThinking) {
            return "<pensamento>\n" + thinking + "\n</pensamento>\n" + content;
        }
        return content;
    }
}

