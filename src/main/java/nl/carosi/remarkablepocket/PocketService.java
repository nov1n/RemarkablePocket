package nl.carosi.remarkablepocket;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import nl.carosi.remarkablepocket.model.Article;
import org.springframework.beans.factory.annotation.Value;
import pl.codeset.pocket.Pocket;
import pl.codeset.pocket.modify.ArchiveAction;
import pl.codeset.pocket.modify.ModifyItemCmd;
import pl.codeset.pocket.read.ContentType;
import pl.codeset.pocket.read.DetailType;
import pl.codeset.pocket.read.GetItemsCmd;
import pl.codeset.pocket.read.ItemState;
import pl.codeset.pocket.read.PocketItem;
import pl.codeset.pocket.read.Sort;

final class PocketService {
    private final String tagFilter;
    private final Pocket pocket;

    public PocketService(@Value("${pocket.tag-filter}") String tagFilter, Pocket pocket) {
        this.tagFilter = tagFilter;
        this.pocket = pocket;
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
