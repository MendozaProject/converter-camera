package com.devsquid.convertercamera.ui.selector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devsquid.convertercamera.ui.CurrencyListItemData
import com.devsquid.convertercamera.ui.MODE_SET_CONVERT_CURRENCY
import com.devsquid.convertercamera.ui.MODE_SET_ORIGINAL_CURRENCY
import com.devsquid.convertercamera.ui.theme.Dimens
import org.koin.androidx.compose.koinViewModel

@Composable
fun CurrencySelectorScreen(viewModel: CurrencySelectorViewModel = koinViewModel(), onBack: () -> Unit) {
    val currenciesState by viewModel.currenciesState.collectAsStateWithLifecycle()
    val filterQuery by viewModel.filterQuery.collectAsStateWithLifecycle()

    CurrencySelector(
        currencies = currenciesState,
        filterQuery = filterQuery,
        mode = viewModel.mode,
        onFilter = viewModel::onFilter,
        onSelect = viewModel::onSelect,
        onBack = onBack,
    )
}

@Composable
fun CurrencySelector(
    currencies: List<CurrencyListItemData>,
    filterQuery: String,
    mode: Int,
    onFilter: (String) -> Unit,
    onSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.topBarHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = when (mode) {
                            MODE_SET_ORIGINAL_CURRENCY -> "Convert To"
                            MODE_SET_CONVERT_CURRENCY -> "Convert From"
                            else -> ""
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = onFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.paddingExtraLarge, vertical = Dimens.paddingMedium),
                    placeholder = {
                        Text("Search by name or ISO code",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(Dimens.cornerRadiusMedium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    )
                )
            }
        },
    ) { innerPadding ->
        if (currencies.isEmpty()) {
            EmptyListView(Modifier.padding(innerPadding))
        } else {
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) {
                CurrencyList(
                    currencies,
                    onSelect,
                    onBack
                )
            }
        }
    }
}

@Composable
fun CurrencyListItem(
    currencyName: String,
    isoCode: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val labelStyle = TextStyle(
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
    Box(
        Modifier
            .background(
                if (highlight) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Dimens.paddingHuge, Dimens.paddingExtraLarge)
        ) {
            Text(
                text = currencyName,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = isoCode,
                style = labelStyle
            )
        }
    }
}

@Composable
fun CurrencyList(
    convertedCurrencyList: List<CurrencyListItemData>,
    onSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(convertedCurrencyList.size) { index ->
            val item = convertedCurrencyList[index]
            CurrencyListItem(
                currencyName = item.name,
                isoCode = item.isoCode,
                onClick = {
                    onSelect(item.isoCode)
                    onBack()
                },
            )
        }
    }
}

@Composable
fun EmptyListView(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = "NO DATA",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Preview
@Composable
fun PreviewRatesList() {
    CurrencySelector(
        currencies = listOf(
            CurrencyListItemData(isoCode = "GBP", name = "British Pound Sterling"),
            CurrencyListItemData(isoCode = "CAD", name = "Canadian Dollar"),
            CurrencyListItemData(isoCode = "JPY", name = "Japanese Yen"),
            CurrencyListItemData(isoCode = "INR", name = "Indian Rupee"),
            CurrencyListItemData(isoCode = "USD", name = "US Dollar"),
            CurrencyListItemData(isoCode = "STD", name = "Sao Tome and Principe Dobra (pre-2018)")
        ),
        filterQuery = "",
        mode = 0,
        onFilter = { _: String ->  },
        onSelect = { _: String -> },
        onBack = {}
    )
}
