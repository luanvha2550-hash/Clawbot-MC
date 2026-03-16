package net.shasankp000.ChatUtils;

import ai.djl.modality.Classifications;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.models.chat.*;

import net.fabricmc.loader.api.FabricLoader;
import net.shasankp000.AIPlayer;
import net.shasankp000.ChatUtils.CART.CartClassifier;
import net.shasankp000.ChatUtils.DecisionResolver.DecisionResolver;
import net.shasankp000.ChatUtils.LIDSNetModel.LIDSNetModelManager;
import net.shasankp000.ChatUtils.PreProcessing.NLPModelSetup;
import net.shasankp000.ChatUtils.PreProcessing.OpenNLPProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NLPProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger("NLPProcessor");
    private static final OllamaAPI ollamaAPI = new OllamaAPI("http://localhost:11434/");
    private static final String bertModelURL = "https://github.com/shasankp000/AI-Player/releases/download/v1.0.5-release-1.20.6-NLP-asset/distilbert-finetuned-intent-torchscript.zip";
    private static final String bertModelExpectedHash = "2356cecf61ee2bbea5cea406253b67aeee6ad21ce74dada64e8be730cc016bdf";
    private static final String cartZipURL = "https://github.com/shasankp000/AI-Player/releases/download/v1.0.5-release-1.20.6-NLP-asset/cart.zip";
    private static final String cartExpectedHash = "1b8e0cc8c5fdb1bdb579b9f916c735929e82ab492a3c3cd13d0a254e765f7d22";
    private static final String LIDSNetModelURL = "https://github.com/shasankp000/AI-Player/releases/download/v1.0.5-release-1.20.6-NLP-asset/LIDSNet_torchscript.zip";
    private static final String LIDSNetExpectedHash = "ad93089b9bfa735d472ab828942541d0d80203dbfd42a3a5bc303a8bc12158e8";
    private static String selectedLM = AIPlayer.CONFIG.getSelectedLanguageModel();
    private static Map<String, String> checkSumFileNameMap = getcheckSumFileNameMap();

    public enum Intent {
        REQUEST_ACTION,      // Ações simples: "mine", "venha cá"
        ASK_INFORMATION,      // Perguntas: "onde está?"
        GENERAL_CONVERSATION, // Conversa: "oi", "legal"
        COMPLEX_ACTION,       // Ações complexas que precisam de mais processamento
        UNSPECIFIED           // Mensagem ambígua
    }



    public static void ensureLocalNLPModel() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path modelDir = configDir.resolve("ai-player/NLPModels");
        Path torchZipFile = modelDir.resolve("distilbert-finetuned-intent-torchscript.zip");
        Path torchModelDir = modelDir.resolve("distilbert-finetuned-intent-torchscript/");
        Path LidsNetModelDir = modelDir.resolve("LIDSNet_torchscript/");
        Path LidsNETZipFile = modelDir.resolve("LIDSNet_torchscript.zip");
        Path cartZipFile = modelDir.resolve("cart.zip");
        Path cartDir = modelDir.resolve("cart_files");
        Path openNlpModelsDir = modelDir.resolve("OpenNLPModels");

        int maxRetries = 2;
        int currentRetry = 0;

        // Check if all models already exist - skip download if so
        boolean allModelsExist = Files.exists(torchModelDir) &&
                                 Files.exists(cartDir) &&
                                 Files.exists(openNlpModelsDir) &&
                                 Files.exists(LidsNetModelDir);

        if (allModelsExist) {
            LOGGER.info("✅ All NLP models already downloaded - skipping download");
            return;
        }

        // Calculate total steps for progress reporting
        int totalSteps = 0;
        if (!Files.exists(torchModelDir)) totalSteps += 2; // Download + Extract BERT
        if (!Files.exists(cartDir)) totalSteps += 2; // Download + Extract CART
        if (!Files.exists(openNlpModelsDir)) totalSteps += checkSumFileNameMap.size(); // Download each OpenNLP model
        if (!Files.exists(LidsNetModelDir)) totalSteps += 2; // Download + Extract LIDSNet

        net.shasankp000.Overlay.NLPDownloadProgressManager.startDownload(totalSteps);
        int currentStepNum = 0;

        try {
            if (!Files.exists(modelDir)) {
                Files.createDirectories(modelDir);
                LOGGER.info("✅ Created NLP model directory: {}", modelDir);
            }

            // download fine-tuned BERT file.

            if (!Files.exists(torchModelDir)) {
                currentStepNum++;
                net.shasankp000.Overlay.NLPDownloadProgressManager.updateProgress("Downloading BERT NLP model...", currentStepNum);
                LOGGER.info("📥 BERT NLP model not found — downloading for first-time setup...");

                try (InputStream in = new URL(bertModelURL).openStream()) {
                    Files.copy(in, torchZipFile, StandardCopyOption.REPLACE_EXISTING);
                }

                // Validate file integrity
                String hash = sha256Hex(torchZipFile);

                if (!hash.equalsIgnoreCase(bertModelExpectedHash)) {

                    while (currentRetry<=maxRetries) {
                        currentRetry+=1;

                        LOGGER.warn("⚠️ BERT NLP model hash mismatch! Re-downloading....");
                        net.shasankp000.Overlay.NLPDownloadProgressManager.updateProgress("Verifying BERT model (retry " + currentRetry + ")...", currentStepNum);

                        Files.deleteIfExists(torchZipFile);

                        try (InputStream in = new URL(bertModelURL).openStream()) {
                            Files.copy(in, torchZipFile, StandardCopyOption.REPLACE_EXISTING);
                        }

                        LOGGER.info("✅ BERT NLP model re-downloaded to {}", torchZipFile);

                        if (!hash.equalsIgnoreCase(bertModelExpectedHash)) {
                            continue;
                        }
                        else {
                            break;
                        }
                    }

                    if (currentRetry == maxRetries) {
                        LOGGER.error("Error! Max retries reached and NLP models are still not downloaded. Please check your internet connection or contact developer.");
                        net.shasankp000.Overlay.NLPDownloadProgressManager.setError("BERT model download failed");
                        throw new IOException("Max retries reached for BERT model and checksum still mismatch");
                    }


                }
                else {
                    LOGGER.info("✅ BERT NLP model file at 100% integrity.");
                }

                currentRetry = 0; // reset to zero

                LOGGER.info("✅ BERT NLP model downloaded to {}", torchZipFile);
            }
            else {

                LOGGER.info("BERT model files already exist! Skipping download....");
            }

            // download CART zip file

            if (!Files.exists(cartDir)) {
                currentStepNum++;
                net.shasankp000.Overlay.NLPDownloadProgressManager.updateProgress("Downloading CART NLP model...", currentStepNum);
                LOGGER.info("📥 CART NLP model not found — downloading for first-time setup...");

                try (InputStream in = new URL(cartZipURL).openStream()) {
                    Files.copy(in, cartZipFile, StandardCopyOption.REPLACE_EXISTING);
                }

                LOGGER.info("✅ CART NLP model downloaded to {}", cartZipFile);


                // Validate file integrity
                String hash = sha256Hex(cartZipFile);

                if (!hash.equalsIgnoreCase(cartExpectedHash)) {

                    while (currentRetry <= maxRetries) {
                        currentRetry += 1;

                        LOGGER.warn("⚠️ CART NLP model hash mismatch! Re-downloading....");
                        net.shasankp000.Overlay.NLPDownloadProgressManager.updateProgress("Verifying CART model (retry " + currentRetry + ")...", currentStepNum);

                        Files.deleteIfExists(cartZipFile);

                        try (InputStream in = new URL(cartZipURL).openStream()) {
                            Files.copy(in, cartZipFile, StandardCopyOption.REPLACE_EXISTING);
                        }

                        LOGGER.info("✅ CART NLP model re-downloaded to {}", cartZipFile);

                        if (!hash.equalsIgnoreCase(cartExpectedHash)) {
                            continue;
                        } else {
                            break;
                        }
                    }

                    if (currentRetry == maxRetries) {
                        LOGGER.error("Error! Max retries reached and NLP models are still not downloaded. Please check your internet connection or contact developer.");
                        net.shasankp000.Overlay.NLPDownloadProgressManager.setError("CART model download failed");
                        throw new IOException("Max retries reached for CART model and checksum still mismatch");
                    }


                } else {
                    LOGGER.info("✅ CART NLP model file at 100% integrity.");
                }
            }
            else {
                LOGGER.info("CART files already exist! Skipping download....");
            }

            // download OpenNLP Models

            if (!Files.exists(openNlpModelsDir)) {
                LOGGER.info("📥 OpenNLP models not found — downloading for first-time setup...");
                Files.createDirectories(openNlpModelsDir);
                LOGGER.info("✅ Created NLP model directory: {}", openNlpModelsDir);

                for (Map.Entry<String, String> entry : checkSumFileNameMap.entrySet()) {
                    String modelURL = entry.getKey();
                    String expectedSha512 = entry.getValue();

                    try {
                        URL url = new URL(modelURL);
                        String fileName = Paths.get(url.getPath()).getFileName().toString();

                        Path targetFilePath = openNlpModelsDir.resolve(fileName);

                        currentStepNum++;
                        net.shasankp000.Overlay.NLPDownloadProgressManager.updateProgress("Downloading OpenNLP: " + fileName, currentStepNum);
                        LOGGER.info("Attempting to download and verify: {}", modelURL);
                        NLPModelSetup.ensureModelDownloaded(targetFilePath, modelURL, expectedSha512);

                    } catch (MalformedURLException e) {
                        LOGGER.error("Invalid URL for model: {} - {}", modelURL, e.getMessage());
                        net.shasankp000.Overlay.NLPDownloadProgressManager.setError("OpenNLP download failed");
                    } catch (IOException e) {
                        LOGGER.error("Failed to download or verify model from {}: {}", modelURL, e.getMessage());
                        net.shasankp000.Overlay.NLPDownloadProgressManager.setError("OpenNLP download failed");
                    } catch (Exception e) {
                        LOGGER.error("An unexpected error occurred for model {}: {}", modelURL, e.getMessage());
                        net.shasankp000.Overlay.NLPDownloadProgressManager.setError("OpenNLP download failed");
                    }
                }

                LOGGER.info("All OpenNLP model downloads and verifications done to {}", openNlpModelsDir);
            }
            else {
                LOGGER.info("OpenNLP model files already exist! Skipping download....");
            }

            // download LIDSNet

            if (!Files.exists(LidsNetModelDir)) {
                currentStepNum++;
                net.shasankp000.Overlay.NLPDownloadProgressManager.updateProgress("Downloading LIDSNet model...", currentStepNum);
                LOGGER.info("📥 LIDSNet NLP model not found — downloading for first-time setup...");

                try (InputStream in = new URL(LIDSNetModelURL).openStream()) {
                    Files.copy(in, LidsNETZipFile, StandardCopyOption.REPLACE_EXISTING);
                }

                // Validate file integrity
                String hash = sha256Hex(LidsNETZipFile);

                if (!hash.equalsIgnoreCase(LIDSNetExpectedHash)) {

                    while (currentRetry<=maxRetries) {
                        currentRetry+=1;

                        LOGGER.warn("⚠️ LIDSNet NLP model hash mismatch! Re-downloading....");
                        net.shasankp000.Overlay.NLPDownloadProgressManager.updateProgress("Verifying LIDSNet (retry " + currentRetry + ")...", currentStepNum);

                        Files.deleteIfExists(LidsNETZipFile);

                        try (InputStream in = new URL(LIDSNetModelURL).openStream()) {
                            Files.copy(in, LidsNETZipFile, StandardCopyOption.REPLACE_EXISTING);
                        }

                        LOGGER.info("✅ LIDSNet NLP model re-downloaded to {}", LidsNETZipFile);

                        if (!hash.equalsIgnoreCase(LIDSNetExpectedHash)) {
                            continue;
                        }
                        else {
                            break;
                        }
                    }

                    if (currentRetry == maxRetries) {
                        LOGGER.error("Error! Max retries reached and NLP models are still not downloaded. Please check your internet connection or contact developer.");
                        net.shasankp000.Overlay.NLPDownloadProgressManager.setError("LIDSNet download failed");
                        throw new IOException("Max retries reached for LIDSNet model and checksum still mismatch");
                    }


                }
                else {
                    LOGGER.info("✅ LIDSNet NLP model file at 100% integrity.");
                }

                currentRetry = 0; // reset to zero

                LOGGER.info("✅ LIDSNet NLP model downloaded to {}", torchZipFile);
            }
            else {
                LOGGER.info("LIDSNet model files already exist! Skipping download....");
            }


            // extract BERT TorchScript-model zip file

            if (Files.exists(torchZipFile)) {
                // extract only if the zip file exists
                currentStepNum++;
                net.shasankp000.Overlay.NLPDownloadProgressManager.updateProgress("Extracting BERT model...", currentStepNum);
                System.out.println("Extracting distilBERT PyTorch[TorchScript] model...");
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(torchZipFile.toFile()))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        Path filePath = modelDir.resolve(entry.getName());
                        if (entry.isDirectory()) {
                            Files.createDirectories(filePath);
                        } else {
                            Files.createDirectories(filePath.getParent());
                            Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }

            else {
                LOGGER.warn("Error, file {} doesn't exist for extraction! Ignore if already extracted previously.", torchZipFile);
            }

            // extract CART zip file

            if (Files.exists(cartZipFile)) {
                currentStepNum++;
                net.shasankp000.Overlay.NLPDownloadProgressManager.updateProgress("Extracting CART model...", currentStepNum);
                System.out.println("Extracting CART files...");
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(cartZipFile.toFile()))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        Path filePath = cartDir.resolve(entry.getName());
                        if (entry.isDirectory()) {
                            Files.createDirectories(filePath);
                        } else {
                            Files.createDirectories(filePath.getParent());
                            Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
            else {
                LOGGER.warn("Error, file {} doesn't exist for extraction! Ignore if already extracted previously.", cartZipFile);
            }

            // extract LIDSNet zip file

            if (Files.exists(LidsNETZipFile)) {
                currentStepNum++;
                net.shasankp000.Overlay.NLPDownloadProgressManager.updateProgress("Extracting LIDSNet model...", currentStepNum);
                System.out.println("Extracting LIDSNet PyTorch[TorchScript] model... ");
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(LidsNETZipFile.toFile()))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        Path filePath = LidsNetModelDir.resolve(entry.getName());
                        if (entry.isDirectory()) {
                            Files.createDirectories(filePath);
                        } else {
                            Files.createDirectories(filePath.getParent());
                            Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
            else {
                LOGGER.warn("Error, file {} doesn't exist for extraction! Ignore if already extracted previously.", cartZipFile);
            }



            // Delete zip after extraction
            Files.deleteIfExists(torchZipFile);
            Files.deleteIfExists(cartZipFile);
            Files.deleteIfExists(LidsNETZipFile);

            // Mark download as complete
            net.shasankp000.Overlay.NLPDownloadProgressManager.completeDownload();
            LOGGER.info("✅ All NLP models successfully downloaded and extracted!");




        } catch (IOException e) {
            LOGGER.error("❌ Failed to ensure NLP model: {}", e.getMessage(), e);
            net.shasankp000.Overlay.NLPDownloadProgressManager.setError("Failed to download NLP models");
        }
    }

    private static Map<String, String> getcheckSumFileNameMap() {
        Map<String, String> checkSumFileNameMap = new HashMap<>();

        // *** IMPORTANT: Replace these URLs with the direct download links from Apache's official models page. ***
        // *** Also, double-check that the checksums are exactly what's provided on that page for the direct links. ***

        checkSumFileNameMap.put("https://dlcdn.apache.org/opennlp/models/ud-models-1.3/opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin", "fed5d53ea87bf66afeb08c084f89ca9b95a96de0920dca40487b2ab9972ee6b7ef211e477db480b7253431de079fd66dfd14329cd7d19ad1f565155eb0cf5191");
        checkSumFileNameMap.put("https://dlcdn.apache.org/opennlp/models/ud-models-1.3/opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin", "147d1cdcb89ca5d9bd0ead8fddf2cc14ae543a9151803fe56b3c1fa152b986605e34fb6dda4819c68aa22fc9bffa6bfab14f916b7d0bf4892d452686af34778a");
        checkSumFileNameMap.put("https://dlcdn.apache.org/opennlp/models/ud-models-1.3/opennlp-en-ud-ewt-lemmas-1.3-2.5.4.bin", "ee81553985908ac25c50fbd8f2ca4222cb42a18845f063c792a1b43639181b70ce20d57b8736a1604367f5e8c59aef55f8463e04004840cbc88abb7123fd9e7f");
        checkSumFileNameMap.put("https://dlcdn.apache.org/opennlp/models/ud-models-1.3/opennlp-en-ud-ewt-pos-1.3-2.5.4.bin", "fca85b38d996b7c422401054feedeb0f748cbc03856893cb27670da5d207315ec9af0c0f818c3107a741a2effca2499b5097ac0b46edf4a5ac10bf77a351ac3e");
        return checkSumFileNameMap;
    }

    // Helper: SHA-256 hash of file contents
    private static String sha256Hex(Path file) throws IOException {
        try (InputStream fis = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("Failed to calculate hash: " + e.getMessage(), e);
        }
    }




    // -------------------------------
    // Primary local prediction entry
    // -------------------------------

    public static Intent getIntention(String userPrompt) {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path modelDir = configDir.resolve("ai-player/NLPModels");
        Path cartDir = modelDir.resolve("cart_files");
        Path vocabFilePath = cartDir.resolve("cart_vectorizer_vocab.json");
        Path labelsFilePath = cartDir.resolve("cart_class_labels.json");
        Path treeFilePath = cartDir.resolve("cart_tree.json");
        Path openNlpModelsDir = modelDir.resolve("OpenNLPModels");
        Path LidsNetModelDir = modelDir.resolve("LIDSNet_torchscript/");

        double bertClassificationConfidence = 0;
        double cartClassificationConfidence = 0;
        double LIDSNetClassificationConfidence = 0;

        CartClassifier cartClassifier = null;

        try {
            File vocabFile = vocabFilePath.toFile();
            File labelFile = labelsFilePath.toFile();
            File treeFile = treeFilePath.toFile();

            cartClassifier = new CartClassifier(treeFile, labelFile, vocabFile);
        } catch (IOException e) {
            LOGGER.error("Error initializing CART classifier! {}", e.getMessage());
        }


        String bertLabel = null;
        String cartLabel = null;
        String LIDSNetLabel = null;
        String decision = null;

        try {
            Classifications intent = AIPlayer.modelManager.predict(userPrompt);
            if (intent != null) {
                bertLabel = intent.best().getClassName();
                bertClassificationConfidence = intent.best().getProbability();

                LOGGER.info("BERT predicted: {} with confidence: {}", bertLabel, bertClassificationConfidence);
            }
        } catch (Exception e) {
            LOGGER.error("Error predicting intent using BERT: {}", e.getMessage());
        }

        try {
            if (cartClassifier != null) {
                CartClassifier.ClassificationResult result = cartClassifier.classify(userPrompt);
                cartLabel = result.label;
                cartClassificationConfidence = result.confidence;

                LOGGER.info("CART predicted: {} with confidence: {}", cartLabel, cartClassificationConfidence);
            }
            else {
                throw new Exception("CART classifier is null!");
            }

        } catch (Exception e) {
            LOGGER.error("Error predicting intent using CART: {}", e.getMessage());
        }

        try {

            // --- 1. Load Feature Map JSON ---
            ObjectMapper mapper = new ObjectMapper();
            Path actualLidsNetModelDir = LidsNetModelDir.resolve("LIDSNet_torchscript/");
            JsonNode root = mapper.readTree(new File(actualLidsNetModelDir.resolve("lidsnet_feature_map.json").toString()));

            // Class label index map
            TreeMap<Integer, String> classIdxMap = new TreeMap<>();
            root.get("idx2label").fields().forEachRemaining(entry ->
                    classIdxMap.put(Integer.parseInt(entry.getKey()), entry.getValue().asText())
            );
            List<String> classNames = new ArrayList<>(classIdxMap.values());

            // Feature names
            List<String> featureNames = new ArrayList<>();
            root.get("features").forEach(f -> featureNames.add(f.asText()));

            // --- 2. Initialize NLP processor ---
            OpenNLPProcessor openNLP = new OpenNLPProcessor(openNlpModelsDir.toString());

            // --- 3. Analyze user input ---
            List<OpenNLPProcessor.TokenInfo> tokens = openNLP.analyze(userPrompt);

            // --- 4. Build symbolic feature set ---
            Set<String> presentFeatures = new HashSet<>();
            for (OpenNLPProcessor.TokenInfo token : tokens) {
                presentFeatures.add("POS=" + token.posTag);
                presentFeatures.add("lemma=" + token.lemma);
            }

            // --- 5. Construct input vector ---
            float[] inputVector = new float[featureNames.size()];
            for (int i = 0; i < featureNames.size(); i++) {
                inputVector[i] = presentFeatures.contains(featureNames.get(i)) ? 1.0f : 0.0f;
            }

            // --- 6. Classify ---
            LIDSNetModelManager lidsNet = LIDSNetModelManager.getInstance(actualLidsNetModelDir);
            lidsNet.loadModel(classNames);
            LIDSNetModelManager.PredictionResult pred = lidsNet.predictWithConfidence(inputVector, classNames);

            // --- 7. Output
            System.out.printf("[LIDSNet Classifier] Sentence: \"%s\"\nPredicted intent: %s (Confidence: %.2f%%)\n",
                    userPrompt, pred.getClassName(), pred.getConfidencePercentage());

            LIDSNetLabel = pred.getClassName();
            LIDSNetClassificationConfidence = pred.getConfidencePercentage();


        }
        catch (Exception e) {
            LOGGER.error("Error while running inference: {}", e.getMessage());
        }



        try {
            DecisionResolver resolver = new DecisionResolver();
            decision = resolver.resolveIntent(
                    // Player message
                    userPrompt,
                    // BERT model
                    bertLabel, bertClassificationConfidence,
                    // Main CART
                    cartLabel, cartClassificationConfidence,
                    // LIDSNet
                    LIDSNetLabel, LIDSNetClassificationConfidence
            );
        } catch (Exception e) {
            LOGGER.error("Error while resolving the final decision: {}", e.getMessage());
        }

        // Detectar COMPLEX_ACTION baseado em palavras-chave
        String lowerPrompt = userPrompt.toLowerCase();
        if (isComplexAction(lowerPrompt)) {
            LOGGER.info("Detectado comando complexo: {}", userPrompt);
            return Intent.COMPLEX_ACTION;
        }

        return Intent.valueOf(decision);
    }

    /**
     * Detecta se a mensagem é uma ação complexa que precisa de mais processamento
     */
    private static boolean isComplexAction(String message) {
        // Palavras que indicam ações complexas (múltiplos passos)
        String[] complexKeywords = {
            "construa", "construir", "construir uma casa",
            "crie", "criar", "fazer uma fazenda",
            "make a", "build a", "create a",
            "mine diamond", "mine gold",
            "craftar", "craftar uma",
            "vá até", "vá para", "travel to"
        };

        for (String keyword : complexKeywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------
    // Fallback LLM method - AGORA COM CACHE
    // -------------------------------
    public static Intent getIntentionFromLLM(String userPrompt) {
        // Primeiro verifica o cache
        String cachedResponse = LLMResponseCache.get(userPrompt);
        if (cachedResponse != null) {
            LOGGER.debug("Usando resposta em cache para: {}", userPrompt.substring(0, Math.min(30, userPrompt.length())));
            return parseIntentResponse(cachedResponse);
        }

        ollamaAPI.setRequestTimeoutSeconds(600);
        String systemPrompt = buildPrompt();

        try {
            List<io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessage> messages = new java.util.ArrayList<>();
            messages.add(new io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessage(
                    OllamaChatMessageRole.SYSTEM, systemPrompt));
            messages.add(new io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessage(
                    OllamaChatMessageRole.USER, userPrompt));

            net.shasankp000.OllamaClient.OllamaThinkingResponse thinkingResponse =
                    net.shasankp000.OllamaClient.OllamaAPIHelper.smartChat(
                            ollamaAPI,
                            "http://localhost:11434",
                            selectedLM,
                            messages
                    );

            String response = thinkingResponse.getContent().trim();

            // Salva no cache
            LLMResponseCache.put(userPrompt, response);

            // Parseia a resposta
            return parseIntentResponse(response);

        } catch (Exception e) {
            LOGGER.error("LLM fallback failed: {}", e.getMessage(), e);
        }
        return Intent.UNSPECIFIED;
    }

    /**
     * Parseia a resposta do LLM para intent
     */
    private static Intent parseIntentResponse(String response) {
        if (response.equalsIgnoreCase("REQUEST_ACTION") || response.contains("REQUEST_ACTION")) {
            return Intent.REQUEST_ACTION;
        } else if (response.equalsIgnoreCase("ASK_INFORMATION") || response.contains("ASK_INFORMATION")) {
            return Intent.ASK_INFORMATION;
        } else if (response.equalsIgnoreCase("GENERAL_CONVERSATION") || response.contains("GENERAL_CONVERSATION")) {
            return Intent.GENERAL_CONVERSATION;
        }
        return Intent.UNSPECIFIED;
    }

    private static String stripThinkTags(String input) {
        return input.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    private static String buildPrompt() {
        return "Você é um agente de IA em Minecraft. Seu papel é entender a intenção da mensagem do jogador e responder em PORTUGUÊS DO BRASIL.\n\n" +
                "INTENÇÕES POSSÍVEIS:\n" +
                "1. REQUEST_ACTION: O jogador quer que você faça algo (minere, construa, vá a algum lugar, ataque, colete, etc)\n" +
                "2. ASK_INFORMATION: O jogador está perguntando algo (onde está, quanto tempo, informações do jogo)\n" +
                "3. GENERAL_CONVERSATION: Conversa geral,水泥sem pedir ações específicas\n" +
                "4. UNSPECIFIED: Mensagem incompleta ou sem contexto claro\n\n" +
                "EXEMPLOS EM PORTUGUÊS:\n\n" +
                "1. REQUEST_ACTION:\n" +
                "- Pode minerar pedra para mim?\n" +
                "- Pegue madeira\n" +
                "- Vá até as coordenadas 100 64 200\n" +
                "- Mate o zumbi mais próximo\n" +
                "- Construa uma casa\n" +
                "- Colete ferro\n" +
                "- Caçe animais\n" +
                "- Explore a caverna\n" +
                "- Traga comida\n" +
                "- Crafta uma espada\n\n" +
                "2. ASK_INFORMATION:\n" +
                "- Você encontrou diamantes?\n" +
                "- Onde está o jogador mais próximo?\n" +
                "- Que horas são no jogo?\n" +
                "- Quantos corações você tem?\n" +
                "- Por que está escurecendo?\n\n" +
                "3. GENERAL_CONVERSATION:\n" +
                "- Construí uma casa hoje\n" +
                "- O céu está bonito\n" +
                "- Adoro explorar cavernas\n" +
                "- Meu amigo entrou no jogo\n\n" +
                "4. UNSPECIFIED:\n" +
                "- Incompleto: Você pode...\n" +
                "- Ambíguo: Faz algo\n" +
                "- Vago: Faz algo legal\n\n" +
                "PALAVRAS-CHAVE PARA DETECTAR AÇÃO:\n" +
                "mine, miner, pegue, pega, colete, coleta, vá, vai, vamos, construa, construye,build, craft, crafta, ataque, ataca, mate, mata, explore, explor,caçe, cac, siga, segue, traga, traz\n\n" +
                "Sempre responda em PORTUGUÊS DO BRASIL!\n" +
                "         harvest\n" +
                "         attack\n" +
                "         fight\n" +
                "         defend\n" +
                "         slay\n" +
                "         kill\n" +
                "         vanquish\n" +
                "         destroy\n" +
                "         battle\n" +
                "         craft\n" +
                "         create\n" +
                "         make\n" +
                "         build\n" +
                "         forge\n" +
                "         assemble\n" +
                "         trade\n" +
                "         barter\n" +
                "         exchange\n" +
                "         buy\n" +
                "         sell\n" +
                "         explore\n" +
                "         discover\n" +
                "         find\n" +
                "         search\n" +
                "         locate\n" +
                "         scout\n" +
                "         construct\n" +
                "         erect\n" +
                "         place\n" +
                "         set\n" +
                "         farm\n" +
                "         plant\n" +
                "         grow\n" +
                "         cultivate\n" +
                "         use\n" +
                "         utilize\n" +
                "         activate\n" +
                "         employ\n" +
                "         operate\n" +
                "         handle\n" +
                "         check\n" +
                "         search\n" +
                "\n" +
                "         Some of the above keywords are synonyms of each other. (e.g check -> search, kill -> vanquish, gather->collect)\n" +
                "\n" +
                "         So you must be on the lookout for the synonyms of such keywords as well.\n" +
                "\n" +
                "         These keywords fall under the category of action-verbs. Since your purpose is to design the output that will call a function, which will trigger an action, you need to know what a verb is and what action-verbs are to further your ease in selecting the appropriate function.\n" +
                "\n" +
                "         A verb is a a word used to describe an action, state, or occurrence, and forming the main part of the predicate of a sentence, such as hear, become, happen.\n" +
                "\n" +
                "         An action verb (also called a dynamic verb) describes the action that the subject of the sentence performs (e.g., “I  run”).\n" +
                "\n" +
                "         Example of action verbs:\n" +
                "\n" +
                "         We \"traveled\" to Spain last summer.\n" +
                "         My grandfather \"walks\" with a stick.\n" +
                "\n" +
                "         The train \"arrived\" on time.\n" +
                "\n" +
                "         I \"ate\" a sandwich for lunch.\n" +
                "\n" +
                "         All the verbs within quotations cite actions that were caused/triggered.\n" +
                "\n" +
                "         So when you are supplied with a prompt that contain the *keywords* which is provided earlier, know that these are actions which correspond to a particular tool within the provided tools.\n" +
                "\n" +
                "However detecting such keyword and immediately classifying it as an action is incorrect.\n" +
                "\n" +
                "Sometimes sentences like \"So, did you go somewhere recently?\" means to ASK_INFORMATION while making conversation. Remember to analyze the entire sentence.\n" +
                "\n" +
                "RESPOND ONLY AS THE AFOREMENTIONED INTENTION TAGS, i.e REQUEST_ACTION, ASK_INFORMATION, GENERAL_CONVERSATION and UNSPECIFIED, NOT A SINGLE WORD MORE.\n" +
                "\n" +
                "\n" +
                " While returning the intention output, do not say anything else. By anything else, I mean any other word at all. \n" +
                "Do not worry about actually executing this corresponding methods based on the user prompts or conversing with the user, that will be taken care of by another system by analyzing your output. \n" +
                "Thus it is imperative that you output only the intention, and nothing else. \n";

    }

}
