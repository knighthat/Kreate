package app.kreate

import app.kreate.coil3.ImageFactory


object Platform {

    lateinit var imageFactoryProvider: ImageFactory.Provider
}

actual fun getImageFactoryProvider(): ImageFactory.Provider = Platform.imageFactoryProvider