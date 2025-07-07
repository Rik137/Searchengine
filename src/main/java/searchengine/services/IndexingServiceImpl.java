package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.services.serviceinterfaces.IndexingService;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class IndexingServiceImpl implements IndexingService {

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);

    @Override
    public boolean isIndexing() {
        return isIndexing.get();
    }

    @Override
    public boolean startIndexing() {
        if (isIndexing.compareAndSet(false, true)) {
            log.debug("start thread >>> {}", Thread.currentThread().getName());
           new Thread(() -> {
                try {
                    log.debug("начло индексации в потоке {}", Thread.currentThread().getName());
                    Thread.sleep(5000);// заменить на реальную индексацию
                } catch (InterruptedException e) {
                    log.error(e.getLocalizedMessage());
                    Thread.currentThread().interrupt();
                } finally {
                    isIndexing.set(false);
                    log.debug("поток {} закончил работу ", Thread.currentThread().getName());
                    log.debug("сброс флага");// после завершения индексации сбрасываем флаг
                }
            }).start();
            return true;
        }
        return false;
    }
}

