package ploud.rentor.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClientHandle implements Runnable {
    static final List<Runnable> activeClient = Collections.synchronizedList(new ArrayList<>());
    private final Runnable runnable;

    public ClientHandle(Runnable runnable) {
        this.runnable = runnable;
    }
    @Override
    public void run() {
        activeClient.add(runnable);
        runnable.run();
        activeClient.remove(runnable);
    }
}
