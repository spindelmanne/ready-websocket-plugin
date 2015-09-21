[![Build Status](https://travis-ci.org/hschott/ready-websocket-plugin.svg)](https://travis-ci.org/hschott/ready-websocket-plugin)

## Ready! API Websocket Plugin

This plugin adds three TestSteps to the functional testing in Ready! API
* one for publishing messages to an websocket server
* one for receiving (and asserting) messages
* and one for dropping connections

It integrates fully with all other Ready! API features like property expansion, property transfers, data-driven testing, etc.

Install the plugin via the Plugin Manager inside Ready! API.

This plugin also runs with SoapUI 5.2. Install it by copying it to <users home>/.soapuios/plugins/ .

This plugin was inspired by [SmartBear/ready-mqtt-plugin](https://github.com/SmartBear/ready-mqtt-plugin) and lots of code has been taken from it.
Thanks to the original authors.

## Websocket Test Steps

This plugin allows publish messages to websocket servers and receive messages from them. It adds 3 new test steps:

* Publish using Websockets – to publish a message
* Receive Websockets Message – to receive a message
* Drop Websocket connection – to close or terminate a connection with websocket server

### Configure Websocket connections 

Before you start, you have to specify the websocket server which you want to use and configure the connection settings.
Every websocket test step has the 'Connection' combo-box. Choose `<New Connection…>` item to create a new connection.

The 'Create Connection' dialog will appear:

<img width="619" alt="new connection" src="https://cloud.githubusercontent.com/assets/4548589/9987415/fd524612-6047-11e5-9b68-3dff0a23f0e5.png">

You have to specify the following settings for the connection (note that the connection may be used in any test case in the project, so only project level property expansions will work correctly for connection settings):

**Name**

The unique name to identify a connection within test steps (this name will appear in the Connection combo-box of the test steps later).

**Server URI** 

This is the URI of the websocket server. Server URI should contain the protocol being used:
    `ws://`  to connect using a plain TCP socket.
    `wss://` to connect using a secure SSL/TLS socket.
Server URI may also contain a port number. If the port is not specified, it will default to 80 for ws:// server URIs, and 443 for wss:// server URIs.
Example of URI: `ws://localhost:80`

**Authentication**

Check this option if the websocket server requires authentication.

**Login** and **Password**

These fields are required if the MQTT server requires authentication.

**Hide**

If this checkbox is unchecked, the password value will be visible in the Password text edit box. If you want to keep the password hidden, check this box.

After you close the 'Create Connection' dialog by clicking `Ok`, this connection will be assigned to the current test step. To use this connection with another test step, choose it from the 'Connection' combo-box in the test step editor.

If you want to browse all connections related to the project or remove some needless connections, open any Publish or Receive test step editor and click on the `Configure Websocket Connections of the Project` toolbar button:

The 'Configure Connections to Websocket Servers' dialog will appear:
<img width="653" alt="configure connections" src="https://cloud.githubusercontent.com/assets/4548589/9987579/5af2ae64-6049-11e5-9f89-cd4b35c1026a.png">

This dialog allows you to manage all connections used for the current project. 
 

### Publish using Websockets

This test step publishes a message on the selected server. 

<img width="498" alt="publish message" src="https://cloud.githubusercontent.com/assets/4548589/9987698/6dcee222-604a-11e5-88d1-6bc1fbfbb457.png">

**Connection**

Choose the websocket server or select `<New Connection…>` to create a new connection for this test step.

**Configure**

Click this button if you wish to customize the connection selected for this test step. The 'Configure Connection' dialog will appear.

**Message type**

Type of  message to publish. The following values are available:

Send as websocket text message
* JSON
* XML
* Text

Send as websocket binary message
* Content of file
* Integer (4 bytes)
* Long (8 bytes)
* Float
* Double

**Message**

This is the actual payload of the message you want to publish.

**Timeout**

The test step will fail if a connection to websocket server is not established and that message could not be send to the server within a specified period.


### Receive Websocket Message

This test step waits until a message is received from the websocket server and optionaly asserts the message.

<img width="550" alt="receive message" src="https://cloud.githubusercontent.com/assets/4548589/9987897/1531df1e-604c-11e5-8965-f7c967825829.png">

**Connection**

Choose the websocket server or select `<New Connection…>` to create a new connection for this test step.

**Configure**

Click this button if you wish to customize the connection selected for this test step. The 'Configure Connection' dialog will appear.

**Expected message type**

This field specifies how to interpret a received message payload. If a message cannot be treated as a specified type, the test step will fail. The following options are available:

* Text (UTF-8)
* Raw binary data (shown as a hexadecimal digits sequence)
* Integer number
* Float number

**Timeout**

The test step will fail if a message isn't received within a specified period.

**Received message**

The payload of a message which was received as a result of the test step execution.

### Drop Websocket Connection

This test step disconnects from the websocket server which is useful if you are testing scenarios in which dropped connections are a factor.

<img width="646" alt="drop connection" src="https://cloud.githubusercontent.com/assets/4548589/9988001/38ae1f10-604d-11e5-8411-f4a1502346d4.png">

**Connection**

Choose the websocket server or select `<New Connection…>` to create a new connection for this test step.

**Configure**

Click this button if you wish to customize the connection selected for this test step. The 'Configure Connection' dialog will appear.

**Drop method**

You can choose one of these methods:

* 'Send Disconnect message to websocket server' - Send status code `1000` and status reason `drop connection test step` message to the websocket server

* 'Close network connection' - Close the network connection (This behavior simulates some network or client related problems)
 


