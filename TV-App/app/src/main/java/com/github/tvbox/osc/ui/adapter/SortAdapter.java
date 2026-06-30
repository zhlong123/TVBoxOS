package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    private int activePosition = 0;

    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    public int getActivePosition() {
        return activePosition;
    }

    public void setActivePosition(int position) {
        if (getData().isEmpty()) {
            return;
        }
        position = Math.max(0, Math.min(position, getData().size() - 1));
        if (position == activePosition) {
            return;
        }
        int previous = activePosition;
        activePosition = position;
        notifyItemChanged(previous);
        notifyItemChanged(activePosition);
    }

    public void syncActivePosition(int position) {
        if (getData().isEmpty()) {
            activePosition = 0;
            return;
        }
        activePosition = Math.max(0, Math.min(position, getData().size() - 1));
    }

    public void refreshActiveTab() {
        if (activePosition >= 0 && activePosition < getData().size()) {
            notifyItemChanged(activePosition);
        }
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        helper.setText(R.id.tvTitle, item.name);
        boolean active = helper.getAdapterPosition() == activePosition;
        TextView title = helper.getView(R.id.tvTitle);
        title.setTextColor(helper.itemView.getContext().getColor(
                active ? R.color.title_fouse_n : R.color.color_BBFFFFFF));
        title.getPaint().setFakeBoldText(active);
        View indicator = helper.getView(R.id.tvTabIndicator);
        indicator.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        bindFilterIcon(helper, item, active);
    }

    private void bindFilterIcon(BaseViewHolder helper, MovieSort.SortData item, boolean active) {
        ImageView filter = helper.getView(R.id.tvFilter);
        ImageView filterColor = helper.getView(R.id.tvFilterColor);
        if (!active || item.filters == null || item.filters.isEmpty()) {
            filter.setVisibility(View.GONE);
            filterColor.setVisibility(View.GONE);
            return;
        }
        boolean hasSelection = item.filterSelectCount() > 0;
        filter.setVisibility(hasSelection ? View.GONE : View.VISIBLE);
        filterColor.setVisibility(hasSelection ? View.VISIBLE : View.GONE);
    }
}
