# BluetoothSerial
 Ionic BluetoothSerial plugin

## write

Writes data to the serial port.

    bluetoothSerial.write(data, success, failure);

### Description

Function `write` data to the serial port. Data can base64 String.


### Parameters

- __data__: Base64 image
- __success__: Success callback function that is invoked when the connection is successful. [optional]
- __failure__: Error callback function, invoked when error occurs. [optional]

### Quick Example

    // image
    bluetoothSerial.write("base64:iVBORw0KGgoAAAANSUhEUgAAAQIAAAE0CAYAAADHbD3gAAAAAXNSR0I ....", success, failure);

   
    bluetoothSerial.write(data, success, failure);