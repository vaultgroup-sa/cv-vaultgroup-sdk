package za.co.vaultgroup.example;

import com.google.protobuf.Empty;
import cv_saas.CommsServiceGrpc;
import cv_saas.Service;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import za.co.vaultgroup.example.config.LockerState;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Api {
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private static final int DOOR_CLOSED = 0;
    private static final int DOOR_OPEN = 1;

    private static final int LOCKER_UNLOCKED = 0;
    private static final int LOCKER_LOCKED = 1;

    private final CommsServiceGrpc.CommsServiceBlockingStub stub;

    public Api(String grpcTarget) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(grpcTarget)
                .usePlaintext()
                .enableRetry()
                .maxRetryAttempts(MAX_RETRY_ATTEMPTS)
                .build();

        stub = CommsServiceGrpc.newBlockingStub(channel);
    }

    public String getVersion() {
        Service.GetVersionResponse response = stub.getVersion(empty());
        validate("getVersion", response.getResp());
        return response.getVersion();
    }

    public List<LockerState> getLockerStates() {
        Service.GetLockerStatesResponse response = stub.getLockerStates(empty());
        validate("getLockerStates", response.getResp());

        List<LockerState> states = new ArrayList<>(response.getDoorMapCount());

        for (int i = 0; i < response.getDoorMapCount(); i++) {
            LockerState state = decodeLockerState(response.getDoorMap(i), response.getLockerMap(i));

            // Not initialized or something else went wrong.
            if (state == null) {
                return null;
            }

            states.add(state);
        }

        return states;
    }

    public LockerMap getLockerMap() {
        Service.GetLockerMapResponse response = stub.getLockerMap(empty());
        validate("getLockerMap", response.getResp());
        return new LockerMap(response.getNumLockers(), response.getLockersList());
    }

    public void setLockerState(int lockerId, LockerState state) {
        Service.SetLockerStateRequest request = Service.SetLockerStateRequest.newBuilder()
                .setLockerNum(lockerId)
                .setState(convertLockerState(state))
                .build();

        try {
            Service.GeneralResponse response = stub.setLockerState(request);
            validate("setLockerState", response.getResp());
        } catch (Exception e) {
            log.error("Error during setLockerState call", e);
        }
    }

    public void setLockState(int lockerId, boolean isLocked) {
        Service.LockRequest request = Service.LockRequest.newBuilder()
                .setLockerNum(lockerId)
                .build();

        try {
            if (isLocked) {
                validate("lockLocker", stub.lockLocker(request).getResp());
            } else {
                validate("unlockLocker", stub.unlockLocker(request).getResp());
            }
        } catch (Exception e) {
            log.error("Error during setLockState call", e);
        }
    }

    public void buzz(int duration) {
        Service.ToggleBuzzerRequest request = Service.ToggleBuzzerRequest.newBuilder()
                .setDurationMillis(duration)
                .build();

        Service.GeneralResponse response = stub.toggleBuzzer(request);
        validate("toggleBuzzer", response.getResp());
    }

    public void clearScreen() {
        Service.GeneralResponse response = stub.lcdClearScreen(empty());
        validate("lcdClearScreen", response.getResp());
    }

    public void writeScreen(int row, int column, String text) {
        Service.LcdWriteDataRequest request = Service.LcdWriteDataRequest.newBuilder()
                        .setRow(row)
                        .setCol(column)
                        .setText(text)
                        .build();

        Service.GeneralResponse response = stub.lcdWriteData(request);
        validate("lcdWriteData", response.getResp());
    }

    public void triggerDuress() {
        Service.GeneralResponse response = stub.triggerUserDuress(empty());
        validate("triggerUserDuress", response.getResp());
    }

    private LockerState decodeLockerState(int doorState, Service.LockerStateResponseMessage lockerState) {
        if (doorState == DOOR_OPEN) {
            return LockerState.OPEN;
        } else if (doorState == DOOR_CLOSED && lockerState.getInitialized()) {
            switch (lockerState.getState().getState()) {
                case LOCKER_LOCKED:
                    return LockerState.LOCKED;
                default:
                case LOCKER_UNLOCKED:
                    return LockerState.CLOSED;
            }
        }

        return null;
    }

    private int convertLockerState(LockerState state) {
        switch (state) {
            case OPEN:
                throw new IllegalArgumentException("Cannot open the locker door programmatically");

            case CLOSED:
                return LOCKER_UNLOCKED;

            case LOCKED:
                return LOCKER_LOCKED;

            default:
                throw new IllegalArgumentException("Unexpected locker state: " + state);
        }
    }

    private void validate(String endpoint, Service.BasicResponse response) {
        if (!response.getSuccess()) {
            log.error("Error during call to `{}` endpoint, code #{} ({})", endpoint, response.getCode(), response.getErrMsg());
        }
    }

    private Empty empty() {
        return Empty.newBuilder().build();
    }

    @Getter
    @AllArgsConstructor
    public static class LockerMap {
        private final int count;
        private final List<Integer> mapping;
    }
}
