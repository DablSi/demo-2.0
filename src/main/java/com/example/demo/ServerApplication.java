package com.example.demo;


import java.io.*;
import java.util.*;

import javax.servlet.http.HttpServlet;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@EnableAutoConfiguration
@RestController
public class ServerApplication extends HttpServlet {

    private HashMap<String, DeviceData> devices;
    private HashMap<Integer, RoomData> rooms;
    private final int[] colors = new int[]{0xff00ff00, 0xffff0000, 0xff0000ff, 0xff000000, 0xffffffff};

    public ServerApplication() {
        super();
        devices = new HashMap<>();
        rooms = new HashMap<>();
    }

    public String deleteChar(String str) {
        if (str != null && str.length() > 0) {
            if (str.charAt(str.length() - 1) == '"')
                str = str.substring(0, str.length() - 1);
            if (str.charAt(0) == '"')
                str = str.substring(1);
        }
        return str;
    }

    //Добавить девайс
    @RequestMapping(value = "/post", method = RequestMethod.POST)
    public Integer putDevice(@RequestPart String device, Integer room, Long date) {
        if(rooms.containsKey(room) && rooms.get(room).time != null)
            return -1;

        int n = 0;
        device = deleteChar(device);
        devices.put(device, new DeviceData(room));
        if (!rooms.containsKey(room)) {
            rooms.put(room, new RoomData());
            rooms.get(room).deviceList.addLast(device);
            n = 1;
        } else {
            if (!rooms.get(room).deviceList.contains(device)) {
                rooms.get(room).deviceList.addLast(device);
                if (rooms.get(room).deviceList.size() <= colors.length + 1) {
                    if (rooms.get(room).colorIndex2 == colors.length) {
                        rooms.get(room).colorIndex2 = 0;
                        rooms.get(room).colorIndex1++;
                    }
                    if (rooms.get(room).colorIndex1 == rooms.get(room).colorIndex2)
                        rooms.get(room).colorIndex2++;

                    devices.get(device).colors[0] = colors[rooms.get(room).colorIndex1];
                    devices.get(device).colors[1] = colors[rooms.get(room).colorIndex2];
                    rooms.get(room).colorIndex2++;
                }
            }
        }
        if (date != null) {
            rooms.get(room).time = date;
            System.out.println("Время: " + date);
        }
        System.out.println("Получено " + device + " в комнате " + room);
        return n;
    }

    //Добавить координаты
    @RequestMapping(value = "/post/coords", method = RequestMethod.POST)
    public Void putCoords(@RequestPart int room, float x1, float y1, float x2, float y2, int[] color) {
        devices.get(rooms.get(room).deviceList.get(color[0] * colors.length + color[1])).coords = new Coords(x1, y1, x2, y2);
        System.out.println("Coords: " + x1 + "," + y1 + " " + x2 + "," + y2);
        return null;
    }


    //Получить координаты
    @RequestMapping("/get/coords/{device}")
    public Coords getCoords(@PathVariable("device") String device) {
        Coords coords = devices.get(device).coords;
        if (coords != null) {
            System.out.println(device + " получил координаты");
            int n = 0;
            for (String i : devices.keySet()) {
                if (devices.get(i).room.equals(devices.get(device).room))
                    n++;
            }
            if (n <= 1)
                rooms.remove(devices.get(device).room);
        }
        return coords == null ? new Coords(-1, -1, -1, -1) : coords;
    }

    //Получить время запуска
    @RequestMapping("/get/{device}")
    public Long getTime(@PathVariable("device") String device) {
        if (devices.containsKey(device)) {
            int n = 0;
            final int room = devices.get(device).room;
            final Long date = rooms.get(room).time;
            if (date != null) {
                return date;
            }
        }
        return (long) -1;
    }

    //Получить цвет
    @RequestMapping("/get/color/{device}")
    public int[] getColor(@PathVariable("device") String device) {
        return devices.get(device).colors;
    }

    //Получение номера комнаты
    @RequestMapping("/get/room")
    public Integer getRoom() {
        return rooms.size() + 1;
    }

    //Получение индексов цветов
    @RequestMapping("/get/indexes/{room}")
    public int[] getIndexes(@PathVariable("room") int room) {
        int[] indexes = new int[2];
        indexes[0] = rooms.get(room).colorIndex1;
        indexes[1] = rooms.get(room).colorIndex2;
        return indexes;
    }

    //Получение массива цветов
    @RequestMapping("/get/colors")
    public int[] getColors() {
        return colors;
    }

    //Добавить время запуска видео
    @RequestMapping(value = "/post/startVideo", method = RequestMethod.POST)
    public Void putStartVideo(@RequestPart Integer room, Long date) {
        rooms.get(room).videoStart = date;
        System.out.println("Время запуска видео в комнате " + room + " добавлено");
        return null;
    }

    //Получить время запуска видео
    @RequestMapping("/get/startVideo/{device}")
    public Long getStartVideo(@PathVariable("device") String device) {
        if (devices.containsKey(device)) {
            final int room = devices.get(device).room;
            final Long date = rooms.get(room).videoStart;
            if (date != null) {
                rooms.get(room).deviceList.remove(device);
                if (rooms.get(room).deviceList.size() <= 1)
                    rooms.get(room).video = new byte[1];
                return date;
            }
        }
        return (long) 0;
    }

    //Скачать видео
    @GetMapping(value = "/download/{room}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public @ResponseBody
    byte[] getFile(@PathVariable("room") int room) {
        System.out.println("Девайс из комнаты " + room + " получил видео");
        return rooms.get(room).video == null ? new byte[1] : rooms.get(room).video;
    }

    //Загрузить видео на сервер
    @PostMapping(value = "/upload")
    public Void uploadVideo(@RequestPart("video") MultipartFile video, @RequestPart("room") int room) {
        System.out.println("Видео " + video.getOriginalFilename() + " в комнате " + room);
        try {
            rooms.get(room).video = video.getBytes();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    //Поставить паузу || Продолжить воспроизведение
    @PostMapping(value = "/post/pause")
    public Void setPause(@RequestPart("room") int room, @RequestPart("pause") boolean pause) {
        System.out.println((pause ? "Пауза " : "Воспроизведение ") + "в комнате " + room);
        rooms.get(room).isPaused = pause;
        return null;
    }

    //Получение массива цветов
    @RequestMapping("/get/pause/{room}")
    public Boolean getPause(@PathVariable("room") int room) {
        return rooms.get(room).isPaused;
    }

    //Данные каждого гаджета
    private class DeviceData {
        public Integer room;
        public int[] colors = new int[2];
        public Coords coords;

        public DeviceData(int newRoom) {
            room = newRoom;
        }
    }

    //Данные каждой комнаты
    private class RoomData {
        public LinkedList<String> deviceList;
        public Long time, videoStart;
        public byte[] video;
        public int colorIndex1 = 0, colorIndex2 = 1;
        public boolean isPaused = false;

        public RoomData() {
            deviceList = new LinkedList<>();
        }
    }

    //Класс координат
    private class Coords {
        public float x1, y1, x2, y2;

        public Coords(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.x2 = x2;
            this.y1 = y1;
            this.y2 = y2;
        }
    }
}