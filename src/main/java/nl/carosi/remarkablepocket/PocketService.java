package nl.carosi.remarkablepocket;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import nl.carosi.remarkablepocket.model.Article;
import org.springframework.beans.factory.annotation.Value;
import pl.codeset.pocket.Pocket;
import pl.codeset.pocket.PocketAuth;
import pl.codeset.pocket.PocketAuthFactory;
import pl.codeset.pocket.modify.ArchiveAction;
import pl.codeset.pocket.modify.ModifyItemCmd;
import pl.codeset.pocket.read.ContentType;
import pl.codeset.pocket.read.DetailType;
import pl.codeset.pocket.read.GetItemsCmd;
import pl.codeset.pocket.read.ItemState;
import pl.codeset.pocket.read.PocketItem;
import pl.codeset.pocket.read.Sort;

final class PocketService {
    static final String CONSUMER_KEY = "99428-51e4648a4528a1faa799c738";

    private final String accessToken;
    private final String tagFilter;
    private Pocket pocket;

    public PocketService(
            @Value("${pocket.access-token}") String accessToken,
            @Value("${pocket.tag-filter}") String tagFilter) {
        this.accessToken = accessToken;
        this.tagFilter = tagFilter;
    }

    static String getAccessToken(String redirectUrl, Consumer<String> prompt) throws IOException {
        PocketAuthFactory factory = PocketAuthFactory.create(CONSUMER_KEY, redirectUrl);
        prompt.accept(factory.getAuthUrl());
        PocketAuth pocketAuth = factory.create();
        return pocketAuth.getAccessToken();
    }

    @PostConstruct
    private void auth() {
        PocketAuth pocketAuth = PocketAuthFactory.createForAccessToken(CONSUMER_KEY, accessToken);
        this.pocket = new Pocket(pocketAuth);
    }

    List<Article> getArticles() throws IOException {
        GetItemsCmd cmd =
                new GetItemsCmd.Builder()
                        .contentType(ContentType.article)
                        .detailType(DetailType.simple)
                        .tag(tagFilter)
                        .state(ItemState.unread)
                        .sort(Sort.newest)
                        .build();
        List<PocketItem> unreads = pocket.getItems(cmd).getList();
        return unreads.stream()
                .map(e -> Article.of(e.getResolvedId(), e.getResolvedUrl(), e.getResolvedTitle()))
                .collect(Collectors.toList());
    }

    void archive(String id) throws IOException {
        pocket.modify(new ModifyItemCmd.Builder().action(new ArchiveAction(id)).build());
    }
}
