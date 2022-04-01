package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import messaging.Endpoint;
import messaging.Message;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import blatt2.broker.PoisonPill;
import javax.swing.JOptionPane;


public class Broker {
    private final Endpoint endpoint = new Endpoint(4711);
    private final ClientCollection clientCollection = new ClientCollection();
    private int clientID = 1;
    private final int poolSize = 4;
    private final ExecutorService executorService = Executors.newFixedThreadPool(poolSize);
    private boolean stopRequested = false;
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();


    public void broker() {
        executorService.execute(new StopPanel());
        while (!stopRequested) {
            Message message = endpoint.blockingReceive();
            executorService.execute(new BrokerTask(message));
        }
        executorService.shutdown();
    }

    private class BrokerTask implements Runnable {
        private Message msg;
        private BrokerTask(Message msg) {
            this.msg = msg;
        }
        @Override
        public void run() {
            if (msg.getPayload().getClass() == RegisterRequest.class)
                register(msg.getSender());

            if (msg.getPayload().getClass() == DeregisterRequest.class)
                deregister(msg.getSender());

            if (msg.getPayload().getClass() == HandoffRequest.class)
                handoffFish(msg);

            if (msg.getPayload().getClass() == PoisonPill.class)
                stopRequested = true;
        }
    }

    private class StopPanel implements Runnable {
        @Override
        public void run() {
            JOptionPane.showMessageDialog(null, "Press OK button to stop server");
            stopRequested = true;
        }
    }

    private void register(InetSocketAddress isa) {
        String newClientID = "Tank " + clientID;
        clientCollection.add(newClientID, isa);
        endpoint.send(isa, new RegisterResponse(newClientID));
        clientID++;
    }

    private void deregister(InetSocketAddress isa) {
        clientCollection.remove(clientCollection.indexOf(isa));
    }

    private void handoffFish(Message msg) {
        HandoffRequest hor = (HandoffRequest) msg.getPayload();
        FishModel fish = hor.getFish();
        Direction direction = fish.getDirection();
        readWriteLock.readLock().lock();
        if (direction == Direction.RIGHT) {
            endpoint.send(
                    (InetSocketAddress) clientCollection.
                            getRightNeighorOf(clientCollection.indexOf(msg.getSender())),
                    msg.getPayload());
        }
        if (direction == Direction.LEFT) {
            endpoint.send(
                    (InetSocketAddress) clientCollection
                            .getLeftNeighorOf(clientCollection.indexOf(msg.getSender())),
                    msg.getPayload());
        }
        readWriteLock.readLock().unlock();
    }


    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.broker();
    }

}
