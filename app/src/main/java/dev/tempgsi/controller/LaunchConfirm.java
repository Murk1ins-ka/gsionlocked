package dev.tempgsi.controller;
import android.app.Activity;
import android.widget.*;

final class LaunchConfirm {
    static void show(Activity activity,java.util.function.Consumer<Boolean> launch){
        final CheckBox[] preserve=new CheckBox[1];android.content.SharedPreferences prefs=activity.getPreferences(0);
        SystemSheet.show(activity,"GSIONLOCKED / ЗАПУСК","Запустить ColorOS?",body->{
            LinearLayout image=Ui.card(body);Ui.text(image,"ColorOS 17",23,Ui.TEXT);Ui.text(image,"Find X9 Ultra IN · Galaxy S23",14,Ui.MUTED);
            Ui.space(image,12);Ui.text(image,"One UI   →   ColorOS",16,Ui.ACCENT);Ui.text(image,"Адаптивные 120 Гц",13,Ui.MUTED);
            Ui.space(body,8);Ui.text(body,"Сохрани открытые файлы. На время сеанса One UI остановится. Блокировка паролем и биометрия должны быть отключены.",14,Ui.MUTED);
            Ui.space(body,16);LinearLayout back=Ui.card(body);Ui.text(back,"Возврат в One UI",16,Ui.TEXT);Ui.text(back,"Кнопка в приложении или обе кнопки громкости на 3 секунды.",13,Ui.MUTED);
            preserve[0]=new CheckBox(activity);preserve[0].setText("Сохранить root · пробный режим");preserve[0].setTextColor(Ui.TEXT);preserve[0].setTextSize(14);preserve[0].setChecked(prefs.getBoolean("coloros_preserve_root",false));back.addView(preserve[0]);
            Ui.text(back,"Без перезагрузки, если восстановление One UI пройдёт проверки. При сбое — перезагрузка с потерей временного root.",12,Ui.MUTED);
        },"Запустить ColorOS",()->{boolean enabled=preserve[0].isChecked();prefs.edit().putBoolean("coloros_preserve_root",enabled).apply();launch.accept(enabled);});
    }
    static void passwordWarning(Activity activity,Runnable accepted){
        SystemSheet.show(activity,"ПЕРЕД ПЕРВЫМ ЗАПУСКОМ","Отключи пароль в One UI",body->{
            LinearLayout lock=Ui.card(body);Ui.text(lock,"1   Открой настройки блокировки",17,Ui.TEXT);Ui.text(lock,"На Samsung выбери тип блокировки экрана «Нет» или «Провести по экрану».",14,Ui.MUTED);
            LinearLayout bio=Ui.card(body);Ui.text(bio,"2   Отключи биометрию",17,Ui.TEXT);Ui.text(bio,"Убери PIN, пароль, графический ключ и способы разблокировки по биометрии.",14,Ui.MUTED);
            LinearLayout guest=Ui.card(body);Ui.text(guest,"3   Не ставь блокировку в ColorOS",17,Ui.TEXT);Ui.text(guest,"Пароль, отпечаток и Face Unlock в этом порте не поддерживаются.",14,Ui.MUTED);
            Ui.space(body,8);Ui.text(body,"Перед загрузкой gsionlocked проверит блокировку повторно и остановит запуск, если она включена.",13,Ui.MUTED);
        },"Понятно, проверить образ",accepted);
    }
    static void returnWarning(Activity activity,Runnable accepted){
        SystemSheet.show(activity,"GSIONLOCKED / ВОЗВРАТ","Вернуться в One UI?",body->{
            LinearLayout destination=Ui.card(body);Ui.text(destination,"One UI",25,Ui.TEXT);Ui.text(destination,"ColorOS   →   Samsung",16,Ui.ACCENT);
            Ui.space(destination,12);Ui.text(destination,"Приложения и файлы ColorOS останутся в её хранилище.",14,Ui.MUTED);
            Ui.space(body,10);Ui.text(body,"Сохрани открытые файлы перед выходом. Экран может временно погаснуть, пока восстанавливаются службы Samsung.",14,Ui.MUTED);
            Ui.space(body,16);LinearLayout recovery=Ui.card(body);Ui.text(recovery,"Восстановление системы",17,Ui.TEXT);Ui.text(recovery,"Пробный режим пытается вернуть One UI без перезагрузки. Доступ root зависит от KernelSU. При сбое проверки восстановления телефон перезагрузится.",14,Ui.MUTED);
        },"Вернуться в One UI",accepted);
    }
    private LaunchConfirm(){}
}
