package chat;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.page.AppShellConfigurator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class ChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }

    /**
     * Fix per le API OpenAI-compatibili (es. Cerebras) che richiedono l'header
     * "Content-Length" nella richiesta HTTP (HTTP 411 - Length Required).
     *
     * Per default Spring AI usa il chunked transfer encoding, che non include
     * Content-Length. Configurando SimpleClientHttpRequestFactory con
     * bufferRequestBody=true, il body viene bufferizzato prima dell'invio,
     * permettendo di calcolare e allegare automaticamente il Content-Length.
     */
    @Bean
    RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setBufferRequestBody(true);
        return RestClient.builder().requestFactory(factory);
    }
}

@Push
class AppShell implements AppShellConfigurator {}

@Route("")
class ChatView extends VerticalLayout {

    private final ChatClient chatClient;
    private final VerticalLayout messages = new VerticalLayout();

    ChatView(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("Rispondi sempre in italiano.")
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(
                MessageWindowChatMemory.builder()
                    .chatMemoryRepository(new InMemoryChatMemoryRepository())
                    .build()).build())
            .build();
        setSizeFull();

        messages.setSizeFull();
        messages.getStyle().set("overflow-y", "auto");

        var input = new TextField();
        input.setPlaceholder("Ask something...");
        input.setWidthFull();

        var send = new Button("Send", e -> sendMessage(input));
        input.addKeyPressListener(Key.ENTER, e -> sendMessage(input));

        var footer = new HorizontalLayout(input, send);
        footer.setWidthFull();
        footer.expand(input);

        expand(messages);
        add(messages, footer);
    }

    private void sendMessage(TextField input) {
        String question = input.getValue().trim();
        if (question.isEmpty()) return;
        addMessage("You: " + question, "#e3f2fd");
        input.clear();

        var ui = getUI().orElseThrow();
        new Thread(() -> {
            String response = chatClient.prompt().user(question).call().content();
            ui.access(() -> addMessage("AI: " + response, "#f5f5f5"));
        }).start();
    }

    private void addMessage(String text, String bgColor) {
        var msg = new Div();
        msg.setText(text);
        msg.getStyle()
            .set("background", bgColor)
            .set("padding", "8px 12px")
            .set("border-radius", "8px")
            .set("margin-bottom", "4px")
            .set("white-space", "pre-wrap")
            .set("width", "100%");
        messages.add(msg);
    }
}
