package com.metrolist.innertubex.models.response

import com.metrolist.innertubex.models.Runs
import com.metrolist.innertubex.models.Thumbnails
import kotlinx.serialization.Serializable

/**
 * Response from the InnerTube `account/accounts_list` endpoint used to enumerate
 * YouTube channels (brand accounts) associated with the signed-in Google account.
 */
@Serializable
data class AccountsListResponse(
    val actions: List<AccountsListAction> = emptyList(),
) {
    fun extractAccounts(): List<Account> {
        val accounts = mutableListOf<Account>()
        for (action in actions) {
            val popupSections =
                action.getMultiPageMenuAction
                    ?.menu
                    ?.multiPageMenuRenderer
                    ?.sections
                    .orEmpty()
            for (section in popupSections) {
                val sectionRenderer = section.accountSectionListRenderer ?: continue
                for (itemSection in sectionRenderer.contents) {
                    val itemSectionRenderer = itemSection.accountItemSectionRenderer ?: continue
                    for (content in itemSectionRenderer.contents) {
                        val item = content.accountItem ?: continue
                        accounts.add(item.toAccount())
                    }
                }
            }

            val pageContents =
                action.updateChannelSwitcherPageAction
                    ?.page
                    ?.channelSwitcherPageRenderer
                    ?.contents
                    .orEmpty()
            for (content in pageContents) {
                val item = content.accountItemRenderer ?: continue
                accounts.add(item.toAccount())
            }
        }
        return accounts
    }

    data class Account(
        val name: String,
        val byline: String?,
        val channelHandle: String?,
        val thumbnailUrl: String?,
        val isSelected: Boolean,
        val signinUrl: String?,
        val pageId: String?,
        val dataSyncId: String?,
    ) {
        val displayName: String
            get() = name.takeIf { it.isNotBlank() } ?: channelHandle ?: byline ?: "Account"
    }
}

@Serializable
data class AccountsListAction(
    val getMultiPageMenuAction: GetMultiPageMenuAction? = null,
    val updateChannelSwitcherPageAction: UpdateChannelSwitcherPageAction? = null,
)

@Serializable
data class GetMultiPageMenuAction(
    val menu: MultiPageMenu? = null,
)

@Serializable
data class MultiPageMenu(
    val multiPageMenuRenderer: MultiPageMenuRenderer? = null,
)

@Serializable
data class MultiPageMenuRenderer(
    val sections: List<MenuSection> = emptyList(),
)

@Serializable
data class MenuSection(
    val accountSectionListRenderer: AccountSectionListRenderer? = null,
)

@Serializable
data class AccountSectionListRenderer(
    val header: AccountSectionHeader? = null,
    val contents: List<AccountItemSection> = emptyList(),
)

@Serializable
data class AccountSectionHeader(
    val googleAccountHeaderRenderer: GoogleAccountHeaderRenderer? = null,
)

@Serializable
data class GoogleAccountHeaderRenderer(
    val name: Runs? = null,
    val email: Runs? = null,
)

@Serializable
data class AccountItemSection(
    val accountItemSectionRenderer: AccountItemSectionRenderer? = null,
)

@Serializable
data class AccountItemSectionRenderer(
    val contents: List<AccountItemContent> = emptyList(),
)

@Serializable
data class AccountItemContent(
    val accountItem: PopupAccountItem? = null,
)

@Serializable
data class PopupAccountItem(
    val accountName: Runs? = null,
    val accountByline: Runs? = null,
    val channelHandle: Runs? = null,
    val accountPhoto: Thumbnails? = null,
    val isSelected: Boolean = false,
    val serviceEndpoint: AccountItemServiceEndpoint? = null,
)

@Serializable
data class UpdateChannelSwitcherPageAction(
    val page: ChannelSwitcherPage? = null,
)

@Serializable
data class ChannelSwitcherPage(
    val channelSwitcherPageRenderer: ChannelSwitcherPageRenderer? = null,
)

@Serializable
data class ChannelSwitcherPageRenderer(
    val contents: List<PageAccountItemContent> = emptyList(),
)

@Serializable
data class PageAccountItemContent(
    val accountItemRenderer: PageAccountItem? = null,
)

@Serializable
data class PageAccountItem(
    val accountName: Runs? = null,
    val accountByline: Runs? = null,
    val channelHandle: Runs? = null,
    val accountPhoto: Thumbnails? = null,
    val isSelected: Boolean = false,
    val serviceEndpoint: AccountItemServiceEndpoint? = null,
)

@Serializable
data class AccountItemServiceEndpoint(
    val selectActiveIdentityEndpoint: SelectActiveIdentityEndpoint? = null,
)

@Serializable
data class SelectActiveIdentityEndpoint(
    val supportedTokens: List<SupportedToken> = emptyList(),
)

@Serializable
data class SupportedToken(
    val accountSigninToken: AccountSigninToken? = null,
    val pageIdToken: PageIdToken? = null,
    val datasyncIdToken: DatasyncIdToken? = null,
)

@Serializable
data class AccountSigninToken(
    val signinUrl: String? = null,
)

@Serializable
data class PageIdToken(
    val pageId: String? = null,
)

@Serializable
data class DatasyncIdToken(
    val datasyncIdToken: String? = null,
)

private fun PopupAccountItem.toAccount(): AccountsListResponse.Account {
    val endpoint = serviceEndpoint?.selectActiveIdentityEndpoint
    val signinUrl = endpoint?.supportedTokens?.firstNotNullOfOrNull { it.accountSigninToken?.signinUrl }
    val pageId = endpoint?.supportedTokens?.firstNotNullOfOrNull { it.pageIdToken?.pageId }
    val dataSyncId = endpoint?.supportedTokens?.firstNotNullOfOrNull { it.datasyncIdToken?.datasyncIdToken }
    return AccountsListResponse.Account(
        name = accountName?.runs?.firstOrNull()?.text ?: "",
        byline = accountByline?.runs?.firstOrNull()?.text,
        channelHandle = channelHandle?.runs?.firstOrNull()?.text,
        thumbnailUrl = accountPhoto?.thumbnails?.lastOrNull()?.url,
        isSelected = isSelected,
        signinUrl = signinUrl,
        pageId = pageId,
        dataSyncId = dataSyncId,
    )
}

private fun PageAccountItem.toAccount(): AccountsListResponse.Account {
    val endpoint = serviceEndpoint?.selectActiveIdentityEndpoint
    val signinUrl = endpoint?.supportedTokens?.firstNotNullOfOrNull { it.accountSigninToken?.signinUrl }
    val pageId = endpoint?.supportedTokens?.firstNotNullOfOrNull { it.pageIdToken?.pageId }
    val dataSyncId = endpoint?.supportedTokens?.firstNotNullOfOrNull { it.datasyncIdToken?.datasyncIdToken }
    return AccountsListResponse.Account(
        name = accountName?.runs?.firstOrNull()?.text ?: "",
        byline = accountByline?.runs?.firstOrNull()?.text,
        channelHandle = channelHandle?.runs?.firstOrNull()?.text,
        thumbnailUrl = accountPhoto?.thumbnails?.lastOrNull()?.url,
        isSelected = isSelected,
        signinUrl = signinUrl,
        pageId = pageId,
        dataSyncId = dataSyncId,
    )
}
