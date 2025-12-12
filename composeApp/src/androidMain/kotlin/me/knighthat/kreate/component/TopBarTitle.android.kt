package me.knighthat.kreate.component

import android.os.Parcel
import android.os.Parcelable


actual data class TopBarTitle(
    actual val title: String
) : Parcelable {

    constructor(parcel: Parcel) : this(parcel.readString()!!)

    override fun describeContents(): Int = 0
    override fun writeToParcel( dest: Parcel, flags: Int ) = dest.writeString( title )

    companion object CREATOR : Parcelable.Creator<TopBarTitle> {

        override fun createFromParcel( parcel: Parcel ): TopBarTitle = TopBarTitle(parcel)
        override fun newArray( size: Int ): Array<TopBarTitle?> = arrayOfNulls( size )
    }
}