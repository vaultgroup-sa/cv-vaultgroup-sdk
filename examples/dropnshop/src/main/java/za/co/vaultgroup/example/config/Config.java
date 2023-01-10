package za.co.vaultgroup.example.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Config {
    private int lockersCount;
    private List<Integer> mapping;
}
