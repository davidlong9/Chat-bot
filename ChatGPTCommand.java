package commands;

        import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
        import net.dv8tion.jda.api.interactions.commands.OptionMapping;
        import net.dv8tion.jda.api.interactions.commands.OptionType;
        import net.dv8tion.jda.api.interactions.commands.build.OptionData;
        import org.json.JSONArray;
        import org.json.JSONObject;

        import java.io.BufferedReader;
        import java.io.IOException;
        import java.io.InputStreamReader;
        import java.io.OutputStreamWriter;
        import java.net.HttpURLConnection;
        import java.net.URL;
        import java.util.ArrayList;
        import java.util.List;

public class ChatGPTCommand implements ICommand {

    private final String apiKey;

    public ChatGPTCommand(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getName() {
        return "chatgpt";
    }

    @Override
    public String getDescription() {
        return "Ask ChatGPT a question";
    }

    @Override
    public List<OptionData> getOptions() {
        List<OptionData> data = new ArrayList<>();
        data.add(new OptionData(OptionType.STRING, "question", "Your question for ChatGPT", true));
        return data;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping questionOption = event.getOption("question");
        if (questionOption == null) {
            event.reply("You need to provide a question.").setEphemeral(true).queue();
            return;
        }
        String question = questionOption.getAsString();

        // Defer the reply to give more time for processing
        event.deferReply().queue();

        // Execute the chatGPT request in a separate thread to avoid blocking
        new Thread(() -> {
            try {
                String response = chatGPT(question, apiKey);
                event.getHook().editOriginal(response).queue();
            } catch (IOException e) {
                event.getHook().editOriginal("Error: " + e.getMessage()).queue();
            }
        }).start();
    }

    private static String chatGPT(String message, String apiKey) throws IOException {
        String url = "https://api.openai.com/v1/chat/completions";
        String model = "gpt-3.5-turbo"; // Use GPT-4 model

        // Create the HTTP POST request
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Authorization", "Bearer " + apiKey);
        con.setRequestProperty("Content-Type", "application/json");

        // Build the request body
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("model", model);
        JSONArray messages = new JSONArray();
        JSONObject messageObj = new JSONObject();
        messageObj.put("role", "user");
        messageObj.put("content", message);
        messages.put(messageObj);
        jsonBody.put("messages", messages);

        con.setDoOutput(true);
        OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream());
        writer.write(jsonBody.toString());
        writer.flush();
        writer.close();

        // Check for HTTP response code and handle errors
        int responseCode = con.getResponseCode();
        if (responseCode == 429) {
            throw new IOException("You have exceeded your quota. Please check your plan and billing details.");
        }
        if (responseCode != HttpURLConnection.HTTP_OK) {
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()));
            String inputLine;
            StringBuilder errorResponse = new StringBuilder();
            while ((inputLine = errorReader.readLine()) != null) {
                errorResponse.append(inputLine);
            }
            errorReader.close();
            throw new IOException("Request failed with code: " + responseCode + ", " + errorResponse.toString());
        }

        // Get the response
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        // Parse the JSON response
        return extractContentFromResponse(response.toString());
    }

    // This method extracts the response expected from ChatGPT and returns it.
    private static String extractContentFromResponse(String response) {
        JSONObject jsonResponse = new JSONObject(response);
        JSONArray choices = jsonResponse.getJSONArray("choices");
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        return message.getString("content");
    }
}
