package nl.carosi.remarkablepocket;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import nl.carosi.remarkablepocket.model.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import pl.codeset.pocket.Pocket;
import pl.codeset.pocket.modify.ArchiveAction;
import pl.codeset.pocket.modify.ModifyItemCmd;
import pl.codeset.pocket.modify.ModifyResult;
import pl.codeset.pocket.read.ContentType;
import pl.codeset.pocket.read.DetailType;
import pl.codeset.pocket.read.GetItemsCmd;
import pl.codeset.pocket.read.ItemState;
import pl.codeset.pocket.read.PocketItem;
import pl.codeset.pocket.read.Sort;

final class PocketService {
    private static final Logger LOG = LoggerFactory.getLogger(PocketService.class);
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
                .map(e -> Article.of(e.getItemId(), e.getResolvedUrl(), e.getResolvedTitle()))
                .collect(Collectors.toList());
    }

    void archive(String id) throws IOException {
        ModifyResult res =
                pocket.modify(new ModifyItemCmd.Builder().action(new ArchiveAction(id)).build());
        if (res.getStatus() == 0) {
            LOG.error(
                    "Could not archive article on Pocket: {}. Please archive it manually: https://getpocket.com/read/{}.",
                    res.getActionResults(),
                    id);
        }
    }
}
