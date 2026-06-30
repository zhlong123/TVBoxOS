package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.FocusAnimHelper;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import java.util.ArrayList;

public class FastListAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    private String selectedName = "\u5168\u90e8";

    public FastListAdapter() {
        super(R.layout.item_fast_site, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.tvSearchWord, item);
        updateItemSelection(helper.itemView, item);
        FocusAnimHelper.attachSearchWordFocus(helper.itemView);
    }

    public void setSelectedName(String selectedName) {
        this.selectedName = TextUtils.isEmpty(selectedName) ? "\u5168\u90e8" : selectedName;
    }

    public void refreshVisibleSelection(TvRecyclerView list) {
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            int pos = list.getChildAdapterPosition(child);
            if (pos >= 0 && pos < getData().size()) {
                updateItemSelection(child, getData().get(pos));
            }
        }
    }

    private void updateItemSelection(View itemRoot, String item) {
        boolean selected = TextUtils.equals(item, selectedName);
        View frame = itemRoot.findViewById(R.id.searchWordFrame);
        TextView textView = itemRoot.findViewById(R.id.tvSearchWord);
        if (frame != null) {
            frame.setSelected(selected);
            frame.refreshDrawableState();
        }
        if (textView != null) {
            textView.getPaint().setFakeBoldText(selected);
            textView.setTextColor(itemRoot.getContext().getColor(
                    selected ? R.color.ui_focus_ring_bright : R.color.ui_text_primary));
            textView.setSelected(selected);
        }
        View ring = itemRoot.findViewById(R.id.searchWordFocusRing);
        if (ring != null) {
            ring.refreshDrawableState();
        }
    }
}
