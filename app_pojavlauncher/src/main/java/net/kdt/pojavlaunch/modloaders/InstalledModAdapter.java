package net.kdt.pojavlaunch.modloaders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.witherlauncher.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class InstalledModAdapter extends RecyclerView.Adapter<InstalledModAdapter.ViewHolder> {

    private static final String DISABLED_SUFFIX = ".disabled";

    public interface DeleteListener {
        void onDeleteRequested(int position, File modFile, String displayName);
    }

    private final List<File> mMods;
    private final DeleteListener mDeleteListener;

    public InstalledModAdapter(List<File> mods, DeleteListener listener) {
        mMods = new ArrayList<>(mods);
        mDeleteListener = listener;
    }

    public void removeAt(int position) {
        mMods.remove(position);
        notifyItemRemoved(position);
    }

    public void refresh(List<File> newMods) {
        mMods.clear();
        mMods.addAll(newMods);
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return mMods.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_installed_mod, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(mMods.get(position));
    }

    @Override
    public int getItemCount() {
        return mMods.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView mNameView;
        private final SwitchCompat mSwitch;
        private final ImageButton mDeleteBtn;

        ViewHolder(View view) {
            super(view);
            mNameView  = view.findViewById(R.id.installed_mod_name);
            mSwitch    = view.findViewById(R.id.installed_mod_switch);
            mDeleteBtn = view.findViewById(R.id.installed_mod_delete);
        }

        void bind(File mod) {
            boolean enabled = !mod.getName().endsWith(DISABLED_SUFFIX);
            String displayName = enabled
                    ? mod.getName()
                    : mod.getName().substring(0, mod.getName().length() - DISABLED_SUFFIX.length());

            mNameView.setText(displayName);

            mSwitch.setOnCheckedChangeListener(null);
            mSwitch.setChecked(enabled);

            mSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                int pos = getAdapterPosition();
                if (pos == RecyclerView.NO_ID) return;
                File current = mMods.get(pos);
                File target = isChecked
                        ? new File(current.getParent(), displayName)
                        : new File(current.getParent(), displayName + DISABLED_SUFFIX);

                if (current.renameTo(target)) {
                    mMods.set(pos, target);
                } else {
                    btn.setOnCheckedChangeListener(null);
                    btn.setChecked(!isChecked);
                    btn.setOnCheckedChangeListener(this::onCheckedChanged);
                    Toast.makeText(btn.getContext(),
                            R.string.mods_toggle_failed, Toast.LENGTH_SHORT).show();
                }
            });

            mDeleteBtn.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_ID) {
                    mDeleteListener.onDeleteRequested(pos, mMods.get(pos), displayName);
                }
            });
        }

        private void onCheckedChanged(android.widget.CompoundButton btn, boolean checked) {
            bind(mMods.get(getAdapterPosition()));
        }
    }
}
