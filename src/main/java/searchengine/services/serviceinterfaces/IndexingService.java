package searchengine.services.serviceinterfaces;

public interface IndexingService {
    boolean isIndexing();
    boolean startIndexing();
    boolean stopIndexing();
    boolean isPageWithinConfiguredSites(String url);
    boolean indexPage(String url);
}
