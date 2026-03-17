package net.luanvha2550_hash.ChatUtils.DecisionResolver;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.exceptions.ToolInvocationException;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessageRole;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestBuilder;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestModel;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatResult;
import net.luanvha2550_hash.AIPlayer;
import net.luanvha2550_hash.FilingSystem.LLMClientFactory;
import net.luanvha2550_hash.FilingSystem.ManualConfig;
import net.luanvha2550_hash.ServiceLLMClients.LLMClient;
import net.luanvha2550_hash.ServiceLLMClients.LLMServiceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DecisionResolver {

    // Set to your Ollama server and desired model
    private static final String OLLAMA_HOST = "http://localhost:11434/";
    private static OllamaAPI ollamaAPI = new OllamaAPI(OLLAMA_HOST);
    private static final Pattern THINK_BLOCK = Pattern.compile("<think>([\\s\\S]*?)</think>");
    private static final Logger LOGGER = LoggerFactory.getLogger("DecisionResolver");

    public DecisionResolver() {
        ollamaAPI = new OllamaAPI(OLLAMA_HOST);
        ollamaAPI.setRequestTimeoutSeconds(300);
    }

    /**
     * Compose the LLM prompt with all classifier predictions and confidences.
     */
    private String buildPrompt(
            String playerMessage,
            String bertPred, double bertConf,
            String cartMainPred, double cartMainConf,
            String lidsNetPred, double lidsNetConf
    ) {
        return "Você é um resolvedor final de intenções para um mod de IA do Minecraft. " +
                "Sua tarefa é ler as saídas e confianças abaixo de seis classificadores de intenções e deduzir corretamente a intenção do jogador entre as três intenções: GENERAL_CONVERSATION: Apenas conversando, ASK_INFORMATION: Solicitando informações sobre algo, REQUEST_ACTION: Solicitando a execução de uma ação." +
                "Decida entre: REQUEST_ACTION, ASK_INFORMATION, GENERAL_CONVERSATION. Você deve retornar apenas uma das três baseado em seu raciocínio e a saída deve ser exatamente igual aos rótulos, caso contrário o resolvedor falhará.\n" +
                "- Mensagem do jogador: \"" + playerMessage + "\"\n" +
                "- BERT: " + bertPred + " (" + String.format("%.2f", bertConf) + ")\n" +
                "- Main CART: " + cartMainPred + " (" + String.format("%.2f", cartMainConf) + ")\n" +
                "- LIDSNet: " + lidsNetPred + " (" + String.format("%.2f", lidsNetConf) + ")\n" +
                "\nSua decisão: Responda APENAS com uma das opções REQUEST_ACTION, ASK_INFORMATION, GENERAL_CONVERSATION. " +
                "Se for realmente ambíguo, retorne UNSPECIFIED. Sem explicação adicional."+
                "Também, tenha em mente que estes classificadores são muito experimentais, então frequentemente podem retornar saídas que vão totalmente contra sua intuição, como classificar incorretamente uma GENERAL_CONVERSATION como REQUEST_ACTION ou ASK_INFORMATION, ou vice-versa. Em tais instâncias, NÃO PENSE DEMAIS, NÃO CONFIE NOS CLASSIFICADORES e apenas vá com a intenção que o input melhor se encaixa de acordo com você e retorne apenas a intenção, nada mais";
    }

    /**
     * Runs the Ollama LLM as Decision Resolver and returns the intent decision.
     */
    public String resolveIntent(
            String playerMessage,
            String bertPred, double bertConf,
            String cartMainPred, double cartMainConf,
            String lidsNetPred, double lidsNetConf
    ) throws OllamaBaseException, IOException, InterruptedException, ToolInvocationException {

        String prompt = buildPrompt(
                playerMessage,
                bertPred, bertConf,
                cartMainPred, cartMainConf,
                lidsNetPred, lidsNetConf
        );

        String selectedLM = AIPlayer.CONFIG.getSelectedLanguageModel();

        String llmProvider = ManualConfig.getActiveProvider();

        String answer = "";

        switch (llmProvider) {
            case "openai", "claude", "grok", "gemini":
                LLMClient llmClient = LLMClientFactory.createClient(llmProvider);
                if (llmClient!=null) {
                    if (llmClient.isReachable()) {
                        answer = llmClient.sendPrompt("Analyze the user prompt thoroughly and answer only as directed in the user prompt. Do not deviate from the expected output pattern as stated in the user prompt.", prompt);
                    }
                    else {
                        LOGGER.error("Error! {} client is not reachable. Please check your internet connection or try again later", llmClient.getProvider());
                    }
                }
                else {
                    LOGGER.error("Error! {} client is null!", llmProvider);
                }
                break;
            case "ollama":
                OllamaChatRequestModel requestModel = OllamaChatRequestBuilder.getInstance(selectedLM)
                        .withMessage(OllamaChatMessageRole.USER, prompt)
                        .build();


                OllamaChatResult response = ollamaAPI.chat(requestModel);

                answer = response.getResponse().trim();

                break;

            default:
                LOGGER.warn("Unsupported provider detected.");
                return "UNSPECIFIED";
        }

        answer = processLLMOutput(answer);

        System.out.println("Answer: " + answer);

        if (answer.contains("REQUEST_ACTION")) return "REQUEST_ACTION";
        if (answer.contains("ASK_INFORMATION")) return "ASK_INFORMATION";
        if (answer.contains("GENERAL_CONVERSATION")) return "GENERAL_CONVERSATION";
        if (answer.contains("UNSPECIFIED")) return "UNSPECIFIED";
        if (answer.contains("No response!")) return "UNSPECIFIED";
        return "";
    }


    public static String processLLMOutput(String fullResponse) {
        Matcher matcher = THINK_BLOCK.matcher(fullResponse);

        if (matcher.find()) {
            String thinking = matcher.group(1).trim();
            String remainder = fullResponse.replace(matcher.group(0), "").trim();

            LOGGER.debug("Thinking part: {}", thinking);

            if (!remainder.isEmpty()) {
                return remainder;
            }
            else {
                return "No response!";
            }
        } else {
            if (fullResponse != null || !fullResponse.isEmpty()) {
                return fullResponse;
            }
            else {
                return "No response!";
            }
        }
    }

}
