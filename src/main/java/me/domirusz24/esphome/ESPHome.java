/*
    ESPHome spigot plugin
    Copyright (C) 2024 Dominik Ruszczyk

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.domirusz24.esphome;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ESPHome extends JavaPlugin implements Listener {

    static class PinState {
        private final boolean active, locked;
        public PinState(boolean active, boolean locked) {
            this.active = active;
            this.locked = locked;
        }

        public boolean isActive() {
            return active;
        }

        public boolean isLocked() {
            return locked;
        }
    }


    private PinState doMethod(String method, int pin) {
        URL url;
        try {
            url = new URL("http://10.0.1.176/" + method + "?pin=" + pin);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            con.setRequestMethod("GET");
        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        }

        BufferedReader in = null;
        try {
            in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        String inputLine;
        StringBuffer content = new StringBuffer();
        while (true) {
            try {
                if ((inputLine = in.readLine()) == null) break;
            } catch (IOException e) {
                break;
            }
            content.append(inputLine);
        }



        try {
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        con.disconnect();

        boolean active, locked;

        active = content.toString().charAt(0) == '1';

        locked = content.toString().charAt(1) == '1';

        return new PinState(active, locked);
    }

    private CompletableFuture<PinState> doMethodAsync(String method, int pin) {
        CompletableFuture<PinState> state = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            PinState s = doMethod(method, pin);
            Bukkit.getScheduler().runTask(this, () -> {
                state.complete(s);
            });
        });

        return state;
    }



    private CompletableFuture<PinState> toggleState(int pin) {
        return doMethodAsync("toggle", pin);
    }

    private CompletableFuture<PinState> getState(int pin) {
        return doMethodAsync("toggle", pin);
    }

    private CompletableFuture<PinState> setState(int pin, boolean state) {
        return doMethodAsync(state ? "on" : "off", pin);
    }

    public Optional<Integer> getPin(Block block) {
        BlockState state = block.getState();
        if (state instanceof Sign sign) {
            try {
                int pin = Integer.parseInt(sign.getLine(0));
                return Optional.of(pin);
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    @EventHandler
    public void onLever(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked != null) {
                if (clicked.getType() == Material.LEVER) {
                    Switch lever = (Switch) clicked.getBlockData();

                    getPin(clicked.getRelative(0, 1, 0)).ifPresent(pin -> {
                        if (lever.isPowered()) {
                            setState(pin, false).thenAccept(pinState -> {
                                lever.setPowered(pinState.active);
                            });
                        } else {
                            setState(pin, true).thenAccept(pinState -> {
                                lever.setPowered(pinState.active);
                            });
                        }
                    });
                }
            }
        }
    }



    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
