# BluetoothSerial
 This plugin based on <a href="https://github.com/don/BluetoothSerial">Ionic BluetoothSerial plugin</a> You can use All Method in that plugin & I implement new method called image to print base64 image.

## image

    bluetoothSerial.image(data, success, failure);

### Description

Function `image` data to the serial port. Data can be base64 Image.


### Parameters

- __data__: Base64 Image
- __success__: Success callback function that is invoked when the connection is successful. [optional]
- __failure__: Error callback function, invoked when error occurs. [optional]

### Quick Example

    // image
    bluetoothSerial.image("iVBORw0KGgoAAAANSUhEUgAAAQIAAAE0CAYAAADHbD3gAAAAAXNSR0I ....", success, failure);
   
