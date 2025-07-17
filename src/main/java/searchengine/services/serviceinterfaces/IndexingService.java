package searchengine.services.serviceinterfaces;

public interface IndexingService {
    boolean isIndexing();
    boolean startIndexing();
    boolean stopIndexing();
}
