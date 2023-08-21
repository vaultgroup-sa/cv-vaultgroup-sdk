# simulator-rs

## Meet Simulator

This simulator is designed to mimic configuration and behavior of real hardware and API that is being used by the smart locker solutions like CellVault™.
The simulator provides web-based UI to let developers imitate all kinds of interactions between user and hardware interfaces (keypad, push buttons, LCD screen, lockers themselves, etc.).

You're able to create and debug your custom solution using nothing but your computer and then seamlessly move it to a real vault device with minimal or no changes at all.
That is possible because the simulator exposes the same [gRPC](https://grpc.io/) API that is available to you on a real device.

When the simulator is started then an initial setup is done. Initial state for every locked is unlocked and open.
Simulator itself does not provide any business logic exactly like a real hardware. So you will only be able
to close lockers' doors using UI and open them back (unless your configuration uses "Chinese" locks which get locked automatically once door is closed).
All those details are documented below.

In order to enable some useful behavior you need to develop *a business logic app* or use one of [the examples](https://) to have something to start with.

## Quick Start

To run the simulator you need [Docker](https://www.docker.com/) installed on your computer.

First pull the image from public registry:
```shell
docker pull public.ecr.aws/vaultgroup/simulator-rs:latest
```

For ease of use let's give it a short name:
```shell
docker tag public.ecr.aws/vaultgroup/simulator-rs:latest simulator-rs
```

All preparations are done, we can run the simulator right away:
```shell
docker run -p 5000:5000 -p 7777:7777 -p 4200:4200 simulator-rs 5-6-6-3 true true
```

It will take some time to start, and then you can simply navigate to http://localhost:4200 in your web browser to open the UI.

You are going to see an empty LCD screen and keypad that actually does nothing. That's because you need to integrate with API and implement your own
business logic (handle key presses, print some information on LCD screen, lock/unlock the lockers etc).

Right away you can use netcat to receive notifications from the simulated unit:

```shell
nc -l -u 5555
```

Example output:

```text
{"type":"door_opened","vals":[{"k":"locker","v":"1"},{"k":"offset","v":"[0:1]"}]}
{"type":"door_closed","vals":[{"k":"locker","v":"1"},{"k":"offset","v":"[0:1]"}]}
{"type":"key","vals":[{"k":"value","v":"49"}]}
{"type":"key","vals":[{"k":"value","v":"50"}]}
{"type":"key","vals":[{"k":"value","v":"51"}]}
```

Also, you can use any gRPC tool (e.g. BloomRPC or Kreya) to make quick gRPC calls and see how it works. Please check further chapters to get more details
about development of your solution.

## Android

You may want to use an Android tablet or phone to have a decent UI with touch screen. At the development stage you're supposed to run the simulator
on your computer and make sure to connect your Android device to the same network (so you can receive broadcast UDP messages). Also, you will connect
to gRPC API that the simulator exposes.

You can use Android simulator instead of a physical device.

Once you've done with development and ready to use your solution with actual hardware you will need the `cvmain` library to your Android project.

Check [this repo](https://github.com/vaultgroup-sa/cvmain-android-sample) for more details about `cvmain` library.

## Configuration

The command we just ran needs some explanation.

The part `-p 5000:5000 -p 7777:7777 -p 4200:4200` is responsible for port mapping (exposure) configuration.
Once created the docker container will expose ports 5000 (WebSocket Server for communication between UI and the simulator), 7777 (gRPC Server) and 4200 (Web UI).

For more information about port mapping, see [docker documentation](https://docs.docker.com/config/containers/container-networking/).

The part `5-6-6-3 true true` is a configuration of hardware to simulate which stands for `<dimensions> <use_cv_locks> <use_multistate_slave>`:
- `dimensions` property defines how many columns (and how many lockers in each column) a simulated vault should have (e.g. `3-3` means two columns having 3 lockers each);
- `use_cv_locks` is a boolean property which defines a type of locks to be simulated (`true` — CV locks that can be locked or unlocked via API, `false` — "Chinese" locks that can be unlocked via API and lock themselves automatically once a locker door is closed);
- `use_multistate_slave` is a boolean property which enables (if set to `true`) third state for a locker (locked/unlocked + ready_to_open which means that locking mechanism is engaged but can be disengaged by pressing a "lock" button which appears on UI) and also an LED indication (green = unlocked, orange = ready_to_open, red = locked).

## Solution Architecture

To develop your own custom solution it's essential to understand how different components interact with each other.

There are different hardware options available. It's up to you what hardware configuration your solution will be targeted for.
In any case your interaction with a hardware is done via gRPC API exposed by a `cvmain` module.
By default, that API uses port 7777.

A `cvmain` module is all the same for both a real hardware and the simulator. So as the interaction protocols.

gRPC API is good for interactions initiated by *a business logic app* which is specific to your custom solution.
Although events triggered by real or simulated hardware (like "key pressed" or "lock button pressed") are delivered to *a business logic app* via UDP protocol (using default port 5555).
So you will need to listen for UDP packets to get notified (data format is documented below).

## The .proto file

The gRPC proto file is available on the vaultgroup documentation website

## Basic Messages

### Basic Response

Includes details of success or failure of the request. This is primarily for future
extensibility. Unless otherwise specified, it is safe to detect success/failure using the
standard mechanisms provided by the gRPC platform.

```
message BasicResponse {
    bool success = 1;
    string errMsg = 2;
    int32 code = 3;
}
```

A general response provided when no special data needs to be returned

```
message GeneralResponse {
    BasicResponse resp = 1;
}
```

### RPC Endpoints

#### Get Software Version

Returns the cvmain version number

```
  rpc get_version(google.protobuf.Empty) returns (GetVersionResponse);
```

```
message GetVersionResponse {
  BasicResponse resp = 1;

  //will contain the software version number
  string version = 2;

}
```

#### Toggle Buzzer

Turns buzzer on for a user specified duration

```
rpc toggle_buzzer(ToggleBuzzerRequest) returns (GeneralResponse);
```

```
message ToggleBuzzerRequest {
  //the number of millis for which the buzzer must be sounded
  uint32 duration_millis = 1;
}
```

#### Lock a locker

locks the specified locker
```
rpc lock_locker(LockRequest) returns (GeneralResponse);
```

```
message LockRequest {
  //the locker number to locker. The first locker is always 1. The last locker can be determined by
  //a call to get_locker_map()
  uint32 locker_num = 1;
}
```

#### Unlock a locker

unlocks the specified locker
```
rpc unlock_locker(LockRequest) returns (GeneralResponse);
```

See lock_locker() for the LockRequest data type.

#### Retrieve the date and time

retrieves the date and time from the device
```
rpc get_rtc(google.protobuf.Empty) returns (GetRtcResponse);
```

```
message GetRtcResponse {
  BasicResponse resp = 1;

  //the date+time in rfc8601/3399 format, with timezone information
  string datetime = 2;
}
```

#### Set the date and time

sets the date and time on the device. Note that this is periodically synced
automatically to UTC, so it is not recommended this endpoint be used directly

```
rpc set_rtc(SetRtcRequest) returns (GeneralResponse);
```

```
message SetRtcRequest {
    //the date+time in rfc8601/3399 format, with timezone information
    string datetime = 1;
}
```

#### Clear the LCD

Clears the entire LCD screen. This is for the LCD screen connected to the master board only.

```
rpc lcd_clear_screen(google.protobuf.Empty) returns (GeneralResponse);
```

#### Clear 1 line of the LCD

clears the specified line of the LCD screen. This is for the LCD screen connected to the master board only.

```
rpc lcd_clear_line(LcdClearLineRequest) returns (GeneralResponse);
```

```
message LcdClearLineRequest {
    //the number of the line to clear. Lines (rows) are 0-3, inclusive
    uint32 line_num = 1;
}
```

#### Write data to the LCD

writes some data to the LCD screen. This is for the LCD screen connected to the master board only.

```
rpc lcd_write_data(LcdWriteDataRequest) returns (GeneralResponse);
```

```
message LcdWriteDataRequest {
  //0-3
  uint32 row = 1;

  //0-19. -1 means auto-center data in row.
  int32 col = 2;

  //data to write (no more than 20 chars if starting from column 0)
  string text = 3;
}
```

#### Retrieve locker map

Retrieves the mapping for the unit. Every vault comprises multiple columns, controlled
by slave boards. Every slave is uniquely numbered, starting from 0, and can have a maximum
of 15 (for a total of 16 slaves.) Each slave controls a maximum of 6 lockers in the column.
Since locker sizes may vary, some slaves will control 6 lockers, others will control 3
lockers, etc.

A locker map represents the number of slaves, the number of lockers controlled by each slave,
and total lockers configured for a particular vault.

```
rpc get_locker_map(google.protobuf.Empty) returns (GetLockerMapResponse);
```

```
message GetLockerMapResponse {
  BasicResponse resp = 1;

  //an array of the number of lockers in each slave (not necessarily column since we've had products where
  //multiple slaves are used in a single column eg. if a 10-locker column is required)
  repeated uint32 lockers = 2;

  //the total number of lockers in the system. This is just the sum of all integers in the array
  uint32 num_lockers = 3;
}
```

#### Notify the system of a duress

Triggers a duress as per the user's request. For instance, the user may enter a special code on the
keypad to have this duress triggered. The underlying action is hardware specific. For instance, on a
RPI, this typically activates a GPIO pin.

The hardware specific integration is handled by a different
application via an integration layer and can be made to do just about anything (eg. we could send a
message to a rabbit server or call a webhook or something if that's required). This can be
useful, for instance, for users to covertly request assistance in the event of a robbery.

```
rpc trigger_user_duress(google.protobuf.Empty) returns (GeneralResponse);
```

#### Send Audit Message

Allows higher level user applications to take advantage of the vaultgroup auditing facility.
User log messages will be mixed in with vaultgroup messages, but in a private code range allowing
for easy filtering. It is not required that this endpoint be used. Users are free to have their own
logging facilities independent of VG. Should users wish to piggy-back off the vault-group
audit platform, they will need to contact us to provision a facility whereby they can receive
their audit messages.
```
rpc user_audit(UserAuditLogRequest) returns (GeneralResponse);
```

```
message UserAuditLogRequest {
  uint32 code = 1;

  /**
    Valid values are "info", "warning", "error", "fatal" only. All other values will result in an error.
   */
  string level = 2;

  /**
    An optional string. May be used to indicate the source of the error
   */
  string facility = 3;

  /**
    The error message, no more than 1024 bytes
   */
  string description = 4;

  string priority = 5;

  string app = 6;
}
```

#### Send SMS

Submits an SMS for transmission. This will be transmitted via the VG server. Units
will require correct permissions on the server to use this facility, so contact
VG beforehand

```
rpc send_sms(SendSmsRequest) returns (GeneralResponse);
```

```
message SendSmsRequest {
  /* cellphone number */
  string cell_num = 1;

  /* sms data to send */
  string msg = 2;
}
```

#### Authentication token retrieval

Retrieves the authentication token used to log in to VG services. This token
is automatically obtained by the cvmain application. It is made available to
application developers should they wish to access VG server facilities directly
from units. This is generally not recommended, but the facility is available if
required.

```
rpc get_auth_token(google.protobuf.Empty) returns (GetAuthTokenResponse);
```

```
message GetAuthTokenResponse {
  BasicResponse resp = 1;

  /**
    Gives access to a copy of the JWT token used for authentication by this app.
    This allows the client to access VG APIs if required. An empty string i.e. ""
    in a success response means no token is currently available
   */
  string token = 2;
}
```

#### Set state on multistate slave boards

A command to set the state on slave boards running multistate or similar firmware.
this command is NOT available on regular slaves. Multistate slaves include modified
logic such that the door lock button can be used as a door open+lock button to
repeatedly access a locker without further keypad input from the user. On
completion of this cycle, the user cancels the operation from something like
the keypad, by altering the internal slave state logic.

Do not use this endpoint unless your solution uses multistate slaves. Most solutions
do not require this functionality

```
rpc set_locker_state(SetLockerStateRequest) returns (GeneralResponse);
```

```
message SetLockerStateRequest {
  /**
    the locker to access. The first locker is 1
   */
  uint32 locker_num = 1;

  /**
     the state to set the locker to. Valid values are:
     LS_OPEN = 0
     LS_LOCKED = 1
     LS_READY_OPEN = 2

   */
  uint32 state = 2;
}
```

#### Ping

a simple endpoint that can be called to see if the server is operational

```
rpc ping(google.protobuf.Empty) returns (GeneralResponse);
```

#### Retrieve current locker states

returns the states for every door (open/close based on the door switch) and
lock (locked/unlocked)

```
rpc get_locker_states(google.protobuf.Empty) returns (GetLockerStatesResponse);
```

```
message GetLockerStatesResponse {
  BasicResponse resp = 1;

  //an array of integers where -1 means not initialized, 0 means closed, 1 means open.
  //each number is for 1 locker (eg. if there are 20 lockers, there will be 20 items
  //in the array)
  repeated int32 door_map = 2;

  //each item is for 1 locker (eg. if there are 20 lockers, there will be 20 items
  //in the array)
  repeated LockerStateResponseMessage locker_map = 3;
}
```

```
message LockerStateResponseMessage {
  bool initialized = 1;
  LockerStateMessage state = 2;
}
```

```
message LockerStateMessage {
  /**
   * See comment in SetLockerStateRequest
   */
  uint32 state = 1;
}
```

#### Retrieve slave firmware versions

Returns the version number for each slave board. The system will
not start if the wrong slaves and/or locks have been configured. Currently,
slave firmware with major number "1" is for regular use. Slave firmware with
major number "10" is for multistate boards. The underlying "cvmain" application
will fail to start if invalid slave firmware versions are detected.

```
rpc get_slave_firmware(google.protobuf.Empty) returns (GetSlaveFirmwareResponse);
```

```
message GetSlaveFirmwareResponse {
  BasicResponse resp = 1;

  //a value eg. "1.5", where "1" is the major and "5" is the minor
  repeated string firmware = 2;
}
```

#### Set the LED for a locker

Every locker has an optional LED that can be set to off, red, green, or orange. When
a door locks, the LED is automatically set to red. When a door unlocks, the LED is
automatically set to green. If a user attempts to engage the lock via the door button
while the door is still open (as determined by the door microswitch), the LED will fail
to lock and the LED with temporarily flash orange.

This command allows the user to explicitly set the LED color. The operations above will
still be affect the LED color.


```
rpc set_led(SetLedMessage) returns (GeneralResponse);
```

```
message SetLedMessage {
    uint32 locker_num = 1;

    //0 for off, 1 for red, 2 for green, 3 for orange
    uint32 color = 2;
}
```

#### Reboot

This commands allows a remote device to reboot the system. The actual reboot
is performed by an external application or script, and the entire OS is
rebooted.

The command requires a code/token. Currently, the code must be calculated as follows:

The token is made up of data in the text file ${base_dir}/reboot_code, combined
with the utc time in seconds since the epoch rounded down to the last 100
seconds. A dash (-) separates these values. The sha256sum is then taken
of these values, and the lowercase hex encoded value is the hash.

For instance:
```
x=get_file_contents("${base_dir}/reboot_code")

//Suppose the unix time (utc) is t=16045968777. This must be rounded down to
//16045968700. The calculation is: t = t - (t % 100) 
t = get_utc_epoch_time()
t = t - (t % 100) 

the value to hash is: v="${x}-${t}"
token = sha256sum(${v})
```

This system ensures the reboot hash changes every 100 seconds, and the
reboot code is never shared between systems

```
rpc reboot(RebootRequest) returns (GeneralResponse);

message RebootRequest {
  string code = 1;
}
```

### User event callbacks

The application implements a callback system for detection of events such as keypad presses,
door lock/unlock, door open/close.

Notification messages are passed up via UDP. The UDP host and port to which callbacks are sent may
be configured in the cvmain configuration file. Every UDP packet contains one event
only.

By default, the callback system sends data to 127.0.0.1:5555. To view events without
writing code, the simplest way is to use netcat like so (on Linux):

```shell
nc -l -u 5555
```

All notification messages have the same data structure:

```json
{
    "type": "string",
    "vals": [
        {
            "k": "some string key",
            "v": "some string value"
        }
    ]
}
```

#### Door opened notification

```json
{
    "type": "door_opened",
    "vals": [
        {
            "k": "locker",
            "v": "5"
        },
        {
            "k": "offset",
            "v": "[0:4]"
        }
    ]
}
```

Where:
- `locker`: locker refers to the locker number (counting from 1)
- `offset`: offset refers to the slave/locker (slave counts from 0, locker counts from 0)

#### Door closed notification

This is the same as the door opened notification, but the type is "door_closed"

#### Door locked notification

This is the same as the door opened notification, but the type is "door_locked"

#### Door unlocked notification

This is the same as the door opened notification, but the type is "door_unlocked"

#### Key pressed notification

Indicates a key was pressed on the keypad attached to the master board

```json
{
    "type": "key",
    "vals": [
        {
            "k": "value",
            "v": "49"
        }
    ]
}
```

Where "49" is the ASCII value 49, or char '1'. All values are provided in ASCII. Valid
(ASCII) values are the characters 0-9, * and #. These are the only keys supported by the
physical keypad.

#### Duress detected

Indicates the user triggered the duress endpoint.

```json
{
    "type": "duress",
    "vals": [
        {
            "k": "dt",
            "v": "2022-01-01T00:00:00Z"
        }
    ]
}
```

Where:
- "dt" means datetime
- the datetime is provided in RFC8601/RFC3399 format

#### RFID card

An RFID card was detected and read, matched against the internal database, and is valid.

```json
{
    "type": "rfid_card",
    "vals": [
        {
            "k": "card",
            "v": "1234567890",
        },
        {
            "k": "dt",
            "v": "2022-01-01T00:00:00Z"
        }
    ]
}
```

Where:
- The value for the "card" field is the rfid card value
- the value for "dt" is the datetime in rfc8601/3399 format

#### Unknown card detected

Notification sent when an RFID card was read successfully, but it could not be matched
against a valid value in the internal database.

Data structure is same as for "RFID card", but the type is "rfid_unknown"
